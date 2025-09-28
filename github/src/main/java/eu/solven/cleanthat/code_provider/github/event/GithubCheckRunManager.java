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
package eu.solven.cleanthat.code_provider.github.event;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.github.GHCheckRunBuilder.Action;
import org.kohsuke.github.GHCheckRunBuilder.Output;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHRepository;

import com.google.common.base.Ascii;
import com.google.common.base.Throwables;

import eu.solven.cleanthat.config.IDocumentationConstants;
import eu.solven.cleanthat.config.IGitService;
import lombok.extern.slf4j.Slf4j;

/**
 * manages CheckRun in GitHub API
 * 
 * @author Benoit Lacelle
 *
 */
// https://stackoverflow.com/questions/67919168/github-checks-api-vs-check-runs-vs-check-suites
@Slf4j
public class GithubCheckRunManager {

	private static final String ID_CLEANTHAT = "Cleanthat";
	private static final int LIMIT_IDENTIFIER = 20;
	public static final String PERMISSION_CHECKS = "checks";

	private static final int LIMIT_SUMMARY = 65_535;

	final IGitService gitService;

	public GithubCheckRunManager(IGitService gitService) {
		this.gitService = gitService;
	}

	// https://docs.github.com/fr/rest/checks/runs?apiVersion=2022-11-28#create-a-check-run
	public Optional<GHCheckRun> createCheckRun(GithubAndToken githubAuthAsInst,
			GHRepository baseRepo,
			String sha1,
			String eventKey) {
		if (GHPermissionType.WRITE == githubAuthAsInst.getPermissions().get(PERMISSION_CHECKS)) {
			// https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#check_run
			// https://docs.github.com/en/rest/reference/checks#runs
			// https://docs.github.com/en/rest/reference/permissions-required-for-github-apps#permission-on-checks

			// Limitted to 20 characters
			var identifier = Ascii.truncate(gitService.getSha1(), LIMIT_IDENTIFIER, "");
			// Limitted to 40 characters
			var description = "Cleanthat cleaning/refactoring";

			try {
				Optional<GHCheckRun> optExisting = baseRepo.getCheckRuns(sha1)
						.toList()
						.stream()
						.filter(cr -> ID_CLEANTHAT.equalsIgnoreCase(cr.getName()))
						.findAny();

				if (optExisting.isEmpty()) {
					try {
						GHCheckRun newCheckRun = baseRepo.createCheckRun(ID_CLEANTHAT, sha1)
								.withExternalID(eventKey)
								.withDetailsURL(IDocumentationConstants.URL_REPO + "?event=" + eventKey)
								.add(new Action(IDocumentationConstants.GITHUB_APP, description, identifier))
								.add(new Output("Initial event from Github", eventKey))
								.withStatus(Status.IN_PROGRESS)
								.create();

						optExisting = Optional.of(newCheckRun);
					} catch (IOException e) {
						// https://github.community/t/resource-not-accessible-when-trying-to-read-write-checkrun/193493
						LOGGER.warn("Issue creating the CheckRun", e);
						optExisting = Optional.empty();
					}
				}

				return optExisting;
			} catch (IOException e) {
				return Optional.empty();
			}
		} else {
			// Invite users to go into:
			// https://github.com/organizations/solven-eu/settings/installations/9086720
			LOGGER.warn("We are not allowed to write checks (permissions=checks:write)");
			return Optional.empty();
		}
	}

	public void reportFailure(GHCheckRun checkRun, RuntimeException e) {
		try {
			var stackTrace = Throwables.getStackTraceAsString(e);

			// Summary is limited to 65535 chars
			var summary = Ascii.truncate(stackTrace, LIMIT_SUMMARY, "...");

			checkRun.update()
					.withConclusion(Conclusion.FAILURE)
					.withStatus(Status.COMPLETED)

					.add(new Output(e.getMessage() + " (" + e.getClass().getName() + ")", summary))
					.create();
		} catch (IOException ee) {
			LOGGER.warn("Issue marking the checkRun as completed: " + checkRun.getUrl(), ee);
		}
	}

	/**
	 * Helps working with {@link Consumer} which may fail an explicit {@link Exception}
	 * 
	 * @author Benoit Lacelle
	 *
	 * @param <T>
	 */
	public interface ThrowingConsumer<T> {
		void consume(T t) throws IOException;
	}

	public static void ifPresent(Optional<GHCheckRun> optCheckRun, ThrowingConsumer<GHCheckRun> consumer) {
		optCheckRun.ifPresent(c -> {
			try {
				consumer.consume(c);
			} catch (IOException e) {
				// We do not re-throw the Exception as it is considered not a big-deal to fail updating the CheckRun
				LOGGER.warn("Issue updating CheckRun", e);
			}
		});
	}
}
