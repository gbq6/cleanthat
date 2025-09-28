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
package eu.solven.cleanthat.mvn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Charsets;

import eu.solven.cleanthat.code_provider.CleanthatPathHelpers;
import eu.solven.cleanthat.codeprovider.CodeProviderHelpers;
import eu.solven.cleanthat.codeprovider.CodeWritingMetadata;
import eu.solven.cleanthat.codeprovider.ICodeProviderWriter;
import eu.solven.cleanthat.config.ConfigHelpers;
import eu.solven.cleanthat.config.EngineInitializerResult;
import eu.solven.cleanthat.config.GenerateInitialConfig;
import eu.solven.cleanthat.config.pojo.CleanthatRepositoryProperties;
import eu.solven.cleanthat.config.spring.ConfigSpringConfig;
import eu.solven.cleanthat.engine.IEngineLintFixerFactory;
import eu.solven.cleanthat.lambda.AllEnginesSpringConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * This mojo will generate a relevant cleanthat configuration in current folder
 * 
 * 
 * @author Benoit Lacelle
 *
 */
// https://maven.apache.org/guides/plugin/guide-java-plugin-development.html
@Slf4j
@Mojo(name = CleanThatInitMojo.MOJO_INIT,
		// This would be called once and for all
		defaultPhase = LifecyclePhase.NONE,
		threadSafe = true,
		// One may rely on the mvn plugin to initialize a configuration, even if no pom.xml is available
		requiresProject = false)
public class CleanThatInitMojo extends ACleanThatSpringMojo {

	public static final String MOJO_INIT = "init";

	@Override
	protected void checkParameters() {
		Path configPath = getMayNotExistRepositoryConfigPath();

		if (Files.exists(configPath)) {
			throw new IllegalArgumentException("There is already a configuration at: " + configPath);
		}
	}

	@Override
	protected List<Class<?>> springClasses() {
		List<Class<?>> classes = new ArrayList<>();

		classes.add(ConfigSpringConfig.class);
		classes.add(CodeProviderHelpers.class);

		// Needed to generate default configuration given all known languages
		classes.add(AllEnginesSpringConfig.class);

		return classes;
	}

	@Override
	public void doClean(ApplicationContext appContext) throws MojoFailureException, IOException {
		// https://github.com/maven-download-plugin/maven-download-plugin/blob/master/src/main/java/com/googlecode/download/maven/plugin/internal/WGet.java#L324
		if (isRunOnlyAtRoot() && !isThisTheExecutionRoot()) {
			// This will check it is called only if the command is run from the project root.
			// However, it will not prevent the plugin to be called on each module
			getLog().info("maven-cleanthat-plugin:" + MOJO_INIT + " skipped (not project root)");
			return;
		}

		Path configPathFile = getMayNotExistRepositoryConfigPath();
		getLog().info("Configuration path: " + configPathFile);

		if (!checkIfValidToInit(configPathFile)) {
			throw new MojoFailureException(configPathFile,
					"Configuration cannot be generated",
					"Something prevents the generation of a configuration");
		}

		ICodeProviderWriter codeProvider = CleanThatMavenHelper.makeCodeProviderWriter(this);

		var generateInitialConfig =
				new GenerateInitialConfig(appContext.getBeansOfType(IEngineLintFixerFactory.class).values());
		EngineInitializerResult properties;
		try {
			properties = generateInitialConfig.prepareDefaultConfiguration(codeProvider);
		} catch (IOException e) {
			throw new UncheckedIOException("Issue preparing initial config given codeProvider=" + codeProvider, e);
		}

		// 'configPathFile' may be not a children of codeProvider.getRepositoryRoot()
		// for users wiching to write the configuration in a separate location
		writeConfiguration(configPathFile, properties.getRepoProperties());

		Map<Path, String> pathToContent = new LinkedHashMap<>();

		var root = codeProvider.getRepositoryRoot();
		properties.getPathToContents()
				.forEach((k, v) -> pathToContent.put(CleanthatPathHelpers.makeContentPath(root, k), v));

		codeProvider.persistChanges(pathToContent, CodeWritingMetadata.empty());
	}

	public boolean checkIfValidToInit(Path configPathFile) {
		var isValid = true;

		MavenProject project = getProject();
		if (project == null || project.getBasedir() == null) {
			// This happens on folder which has no pom.xml
			// Useful to projects not integrating maven, but wishing to be initialized through the mvn plugin
			LOGGER.info("You are initializing cleanthat without a pom.xml to contextualize it");
		} else {
			Path baseFir = fs.getPath(project.getBasedir().getAbsolutePath());

			if (!baseFir.startsWith(configPathFile)) {
				LOGGER.warn("The configuration is being written out of the project baseDir: '{}' vs '{}'",
						configPathFile,
						baseFir);
			}
		}

		if (Files.isDirectory(configPathFile)) {
			LOGGER.error("The path of the configuration is a folder: '{}'", configPathFile);
			isValid = false;
		} else if (Files.exists(configPathFile)) {
			if (Files.isRegularFile(configPathFile)) {
				LOGGER.error("There is already a configuration: '{}'", configPathFile);
			} else {
				LOGGER.error("There is something but not a file at configuration: '{}'", configPathFile);
			}
			isValid = false;
		} else {
			LOGGER.info("We are about to init a configuration at: '{}'", configPathFile);
		}
		return isValid;
	}

	public void writeConfiguration(Path configPathFile, CleanthatRepositoryProperties properties) throws IOException {
		var yamlObjectMapper = ConfigHelpers.makeYamlObjectMapper();
		String asYaml;
		try {
			asYaml = yamlObjectMapper.writeValueAsString(properties);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Issue converting " + properties + " to YAML", e);
		}

		writeFile(configPathFile, asYaml);
	}

	private void writeFile(Path configPathFile, String content) throws IOException {
		if (!Files.exists(configPathFile.getParent())) {
			Files.createDirectories(configPathFile.getParent());
			LOGGER.info("We created parent folder(s) for {}", configPathFile);
		}

		try {
			// StandardOpenOption.TRUNCATE_EXISTING
			Files.writeString(configPathFile, content, Charsets.UTF_8, StandardOpenOption.CREATE_NEW);
		} catch (IOException e) {
			throw new UncheckedIOException("Issue writing content into: " + configPathFile, e);
		}
	}
}
