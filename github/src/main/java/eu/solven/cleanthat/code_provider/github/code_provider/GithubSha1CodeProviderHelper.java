/*
 * Copyright 2023-2025 Benoit Lacelle - SOLVEN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.solven.cleanthat.code_provider.github.code_provider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.kohsuke.github.GHRepository;
import org.springframework.util.FileSystemUtils;

import com.google.common.collect.Iterables;

import eu.solven.cleanthat.code_provider.local.FileSystemGitCodeProvider;
import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.codeprovider.ICodeProviderFile;
import eu.solven.cleanthat.jgit.JGitCodeProvider;
import eu.solven.pepper.logging.PepperLogHelper;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper for Github sha1
 *
 * @author Benoit Lacelle
 */
@Slf4j
public class GithubSha1CodeProviderHelper {

	// To be compared with the limit of 5000 calls per hour per installation
	private static final int MAX_FILE_BEFORE_CLONING = 512;

	private static final boolean ZIP_ELSE_CLONE = true;

	final AtomicReference<ICodeProvider> localClone = new AtomicReference<>();
	final AtomicReference<Path> localTmpFolder = new AtomicReference<>();

	final IGithubSha1CodeProvider sha1CodeProvider;

	public GithubSha1CodeProviderHelper(IGithubSha1CodeProvider sha1CodeProvider) {
		this.sha1CodeProvider = sha1CodeProvider;
	}

	public int getMaxFileBeforeCloning() {
		return MAX_FILE_BEFORE_CLONING;
	}

	public boolean hasLocalClone() {
		return localClone.get() != null;
	}

	public void listFilesLocally(Consumer<ICodeProviderFile> consumer) throws IOException {
		ensureLocalClone();

		localClone.get().listFilesForContent(consumer);
	}

	/**
	 * 
	 * @return true if we indeed clone locally. False if already cloned locally
	 */
	@SuppressWarnings("PMD.CloseResource")
	protected boolean ensureLocalClone() {
		// TODO Tests against multiple calls: the repo shall be cloned only once
		synchronized (this) {
			if (localClone.get() != null) {
				// The repo is already cloned
				return false;
			}

			// https://github.community/t/cloning-private-repo-with-a-github-app-private-key/14726
			Path workingDir;
			try {
				workingDir = Files.createTempDirectory("cleanthat-clone");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			localTmpFolder.set(workingDir);

			ICodeProvider localCodeProvider;
			if (ZIP_ELSE_CLONE) {
				ICodeProvider zippedLocalRef;
				try {
					zippedLocalRef = downloadGitRefLocally(workingDir);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				// localCodeProvider = new CodeProviderDecoratingWriter(zippedLocalRef, () -> sha1CodeProvider);
				localCodeProvider = zippedLocalRef;
			} else {
				Git jgit = cloneGitRepoLocally(workingDir);
				localCodeProvider = JGitCodeProvider.wrap(workingDir, jgit, sha1CodeProvider.getSha1(), true);
			}
			return localClone.compareAndSet(null, localCodeProvider);
		}
	}

	protected Git cloneGitRepoLocally(Path tmpDir) {
		GHRepository repo = sha1CodeProvider.getRepo();
		LOGGER.info("Cloning the repo {} into {}", repo.getFullName(), tmpDir);

		String rawTransportUrl = repo.getHttpTransportUrl();
		String authTransportUrl = "https://x-access-token:" + sha1CodeProvider.getToken()
				+ "@"
				+ rawTransportUrl.substring("https://".length());

		// It seems we are not allowed to give a sha1 as name
		return JGitCodeProvider.makeGitRepo(tmpDir, authTransportUrl, sha1CodeProvider.getRef());
	}

	protected ICodeProvider downloadGitRefLocally(Path tmpDir) throws IOException {
		String ref = sha1CodeProvider.getSha1();

		// We save the repository zip in this hardcoded file
		var zipPath = tmpDir.resolve("repository.zip");

		// if (!Files.exists(zipPath)) {
		// throw new IllegalStateException("We expect the path to exists: " + zipPath);
		// }
		Files.createDirectories(zipPath.getParent());

		GHRepository repo = sha1CodeProvider.getRepo();
		LOGGER.info("Downloading the repo={} ref={} into {}", repo.getFullName(), ref, zipPath);

		try {
			// https://stackoverflow.com/questions/8377081/github-api-download-zip-or-tarball-link
			// https://docs.github.com/en/rest/reference/repos#download-a-repository-archive-zip
			repo.readZip(inputStream -> {
				long nbBytes = Files.copy(inputStream, zipPath, StandardCopyOption.REPLACE_EXISTING);
				LOGGER.info("We wrote a ZIP of size={} into {}", PepperLogHelper.humanBytes(nbBytes), zipPath);
				return tmpDir;
			}, ref);
		} catch (IOException e) {
			throw new UncheckedIOException("Issue downloading a ZIP for " + ref, e);
		}

		var repoPath = tmpDir.resolve("repository");

		// TODO We may want not to unzip the file, but it would probably lead to terrible performance
		LOGGER.info("Unzipping the repo={} ref={} into {}", repo.getFullName(), ref, repoPath);
		try (var fis = Files.newInputStream(zipPath)) {
			unzip(fis, repoPath);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Issue with " + tmpDir, e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		// Github will generate a ZIP with the content in a root directory
		List<Path> zipRoots;
		try {
			zipRoots = Files.list(repoPath).collect(Collectors.toList());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		if (zipRoots.size() != 1) {
			throw new IllegalStateException("We expected a single directory in the root. Were: " + zipRoots);
		}

		return new FileSystemGitCodeProvider(Iterables.getOnlyElement(zipRoots));
	}

	// https://stackoverflow.com/questions/10633595/java-zip-how-to-unzip-folder
	@SuppressWarnings("PMD.AssignmentInOperand")
	private static void unzip(InputStream is, Path targetDir) throws IOException {
		targetDir = targetDir.toAbsolutePath();
		try (var zipIn = new ZipInputStream(is)) {
			for (ZipEntry ze; (ze = zipIn.getNextEntry()) != null;) {
				var resolvedPath = targetDir.resolve(ze.getName()).normalize();
				if (!resolvedPath.startsWith(targetDir)) {
					// see: https://snyk.io/research/zip-slip-vulnerability
					throw new RuntimeException("Entry with an illegal path: " + ze.getName());
				}
				if (ze.isDirectory()) {
					Files.createDirectories(resolvedPath);
				} else {
					Files.createDirectories(resolvedPath.getParent());
					Files.copy(zipIn, resolvedPath);
				}
			}
		}
	}

	public void cleanTmpFiles() {
		var workingDir = localTmpFolder.get();
		if (workingDir != null) {
			LOGGER.info("Removing recursively the folder: {}", workingDir);

			// In Amazon Lambda, we are limited to 500MB in /tmp
			// https://stackoverflow.com/questions/48347350/aws-lambda-no-space-left-on-device-error
			try {
				FileSystemUtils.deleteRecursively(workingDir);
			} catch (IOException e) {
				LOGGER.info("Issue removing path: " + workingDir, e);
			}
		}
	}
}
