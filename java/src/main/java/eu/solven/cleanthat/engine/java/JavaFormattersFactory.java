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
package eu.solven.cleanthat.engine.java;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.solven.cleanthat.config.ConfigHelpers;
import eu.solven.cleanthat.config.pojo.CleanthatEngineProperties;
import eu.solven.cleanthat.config.pojo.CleanthatStepProperties;
import eu.solven.cleanthat.engine.ASourceCodeFormatterFactory;
import eu.solven.cleanthat.engine.IEngineStep;
import eu.solven.cleanthat.engine.java.refactorer.JavaRefactorer;
import eu.solven.cleanthat.engine.java.refactorer.JavaRefactorerProperties;
import eu.solven.cleanthat.engine.java.refactorer.JavaRefactorerStep;
import eu.solven.cleanthat.formatter.CleanthatSession;
import eu.solven.cleanthat.formatter.ILintFixer;
import eu.solven.cleanthat.formatter.ILintFixerWithId;
import eu.solven.cleanthat.language.IEngineProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Formatter for Java
 *
 * @author Benoit Lacelle
 */
@Slf4j
public class JavaFormattersFactory extends ASourceCodeFormatterFactory {

	public JavaFormattersFactory(ConfigHelpers configHelpers) {
		super(configHelpers);
	}

	@Override
	public String getEngine() {
		return "javaparser";
	}

	@SuppressWarnings("PMD.TooFewBranchesForASwitchStatement")
	@Override
	public ILintFixer makeLintFixer(CleanthatSession cleanthatSession,
			IEngineProperties engineProperties,
			CleanthatStepProperties stepProperties) {
		ILintFixerWithId processor;
		var stepId = stepProperties.getId();
		var parameters = getParameters(stepProperties);

		LOGGER.debug("Processing: {}", stepId);

		switch (stepId) {
		case JavaRefactorerStep.ID_REFACTORER: {
			JavaRefactorerProperties processorConfig = convertValue(parameters, JavaRefactorerProperties.class);
			var javaRefactorer = new JavaRefactorer(engineProperties, processorConfig);

			LOGGER.info("Mutators: {}", javaRefactorer.getMutatorIds());
			processor = javaRefactorer;
			break;
		}

		default:
			throw new IllegalArgumentException("Unknown step: " + stepId);
		}

		if (!processor.getId().equals(stepId)) {
			throw new IllegalStateException("Inconsistency: " + processor.getId() + " vs " + stepId);
		}

		return processor;
	}

	@Override
	public CleanthatEngineProperties makeDefaultProperties(Set<String> steps) {
		var engineBuilder = CleanthatEngineProperties.builder().engine(getEngine());

		if (steps.contains(JavaRefactorerStep.ID_REFACTORER)) {
			engineBuilder.step(CleanthatStepProperties.builder()
					.id(JavaRefactorerStep.ID_REFACTORER)
					.parameters(JavaRefactorerProperties.defaults())
					.build());
		}

		return engineBuilder.build();
	}

	@Override
	public Map<String, String> makeCustomDefaultFiles(CleanthatEngineProperties engineProperties,
			Set<String> subStepIds) {
		return Map.of();
	}

	@Override
	public List<IEngineStep> getMainSteps() {
		return Arrays.asList(new JavaRefactorerStep());
	}

}
