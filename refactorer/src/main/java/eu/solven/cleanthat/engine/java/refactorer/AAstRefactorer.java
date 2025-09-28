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
package eu.solven.cleanthat.engine.java.refactorer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.codehaus.plexus.languages.java.version.JavaVersion;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.meta.IMutator;
import eu.solven.cleanthat.engine.java.refactorer.meta.IReApplyUntilNoop;
import eu.solven.cleanthat.engine.java.refactorer.meta.IWalkingMutator;
import eu.solven.cleanthat.engine.java.refactorer.mutators.composite.AllIncludingDraftCompositeMutators;
import eu.solven.cleanthat.engine.java.refactorer.mutators.composite.AllIncludingDraftSingleMutators;
import eu.solven.cleanthat.engine.java.refactorer.mutators.composite.CompositeMutator;
import eu.solven.cleanthat.formatter.ILintFixerWithId;
import eu.solven.cleanthat.formatter.ILintFixerWithPath;
import eu.solven.cleanthat.formatter.PathAndContent;
import eu.solven.cleanthat.language.IEngineProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is dedicated to refactoring. Most mutators will refactor code to a better (e.g. shorter, faster, safer,
 * etc) but with [strictly|roughly] equivalent runtime behavior.
 *
 * @author Benoit Lacelle
 */
// https://github.com/revelc/formatter-maven-plugin/blob/master/src/main/java/net/revelc/code/formatter/java/JavaFormatter.java
@Slf4j
@SuppressWarnings("PMD.GenericsNaming")
public abstract class AAstRefactorer<AST, P, R, M extends IWalkingMutator<AST, R>>
		implements ILintFixerWithId, ILintFixerWithPath {

	private static final int MAX_REAPPLY = 10;

	private final List<M> mutators;

	public AAstRefactorer(List<M> mutators) {
		this.mutators = ImmutableList.copyOf(mutators);

		this.mutators.forEach(ct -> LOGGER.debug("Using transformer: {}", ct.getIds()));
	}

	public Set<String> getMutatorIds() {
		return mutators.stream().flatMap(m -> m.getIds().stream()).sorted().collect(Collectors.toSet());
	}

	protected Iterable<M> getRawMutators() {
		return mutators;
	}

	public static <AST, P> Optional<AST> parse(AAstRefactorer<AST, P, ?, ?> refactorer, String sourceCode) {
		var parser = refactorer.makeAstParser();

		return refactorer.parseSourceCode(parser, sourceCode);
	}

	protected abstract P makeAstParser();

	protected abstract Optional<AST> parseSourceCode(P parser, String sourceCode);

	@Override
	public String doFormat(PathAndContent pathAndContent) throws IOException {
		return doFormat(pathAndContent.getContent());
	}

	protected String applyTransformers(PathAndContent pathAndContent) {
		AtomicReference<String> refCleanCode = new AtomicReference<>(pathAndContent.getContent());

		// Ensure we compute the compilation-unit only once per String
		AtomicReference<AST> refCompilationUnit = new AtomicReference<>();

		var parser = makeAstParser();

		var firstMutator = new AtomicBoolean(true);
		var inputIsBroken = new AtomicBoolean(false);

		var path = pathAndContent.getPath();

		// TODO What if mutators are applied in order `A->B` but `A` could give good results after `B` being applied?
		getRawMutators().forEach(ct -> {
			int maxNbApply;
			if (ct instanceof IReApplyUntilNoop) {
				// Prevent any infinite loop
				maxNbApply = MAX_REAPPLY;
			} else {
				maxNbApply = 1;
			}

			AstRefactorerInstance<AST, P, R> instance = new AstRefactorerInstance<AST, P, R>(this,
					parser,
					ct,
					refCompilationUnit,
					firstMutator,
					inputIsBroken);

			for (var i = 0; i < maxNbApply; i++) {
				boolean appliedWithChange =
						instance.applyOneMutator(refCleanCode, refCompilationUnit, firstMutator, inputIsBroken, path);
				if (appliedWithChange) {
					LOGGER.debug("Effective change after iteration={}", i);
				} else {
					LOGGER.debug("No more change after iteration={}", i);
					break;
				}
			}
		});
		return refCleanCode.get();
	}

	protected abstract boolean isValidResultString(P parser, String resultAsString);

	public static List<IMutator> filterRules(IEngineProperties engineProperties, JavaRefactorerProperties properties) {
		var languageLevel = engineProperties.getEngineVersion();
		if (Strings.isNullOrEmpty(languageLevel)) {
			languageLevel = IJdkVersionConstants.LAST;
		}
		var engineVersion = JavaVersion.parse(languageLevel);

		var includedRules = properties.getMutators();
		var excludedRules = properties.getExcludedMutators();
		var includeDraft = properties.isIncludeDraft();

		// TODO Enable a custom rule in includedRules (e.g. to load from a 3rd party JAR)
		return filterRules(engineVersion, includedRules, excludedRules, includeDraft);
	}

	public static List<IMutator> filterRules(JavaVersion sourceCodeVersion,
			List<String> includedRules,
			List<String> excludedRules,
			boolean includeDraft) {
		var allSingleMutators =
				new AllIncludingDraftSingleMutators(JavaVersion.parse(IJdkVersionConstants.LAST)).getUnderlyings();
		List<? extends IMutator> allCompositeMutators =
				new AllIncludingDraftCompositeMutators(JavaVersion.parse(IJdkVersionConstants.LAST)).getUnderlyings();

		Set<String> allSingleIds = allSingleMutators.stream()
				.flatMap(m -> m.getIds().stream())
				.collect(Collectors.toCollection(TreeSet::new));
		Set<String> allCompositeIds = allCompositeMutators.stream()
				.flatMap(m -> m.getIds().stream())
				.collect(Collectors.toCollection(TreeSet::new));

		var compatibleSingleMutators = new AllIncludingDraftSingleMutators(sourceCodeVersion).getUnderlyings();
		List<? extends IMutator> compatibleCompositeMutators =
				new AllIncludingDraftCompositeMutators(sourceCodeVersion).getUnderlyings();

		Set<String> compatibleSingleIds = compatibleSingleMutators.stream()
				.flatMap(m -> m.getIds().stream())
				.collect(Collectors.toCollection(TreeSet::new));
		Set<String> compatibleCompositeIds = compatibleCompositeMutators.stream()
				.flatMap(m -> m.getIds().stream())
				.collect(Collectors.toCollection(TreeSet::new));

		var mutatorsMayComposite = includedRules.stream().flatMap(includedRule -> {
			if (JavaRefactorerProperties.WILDCARD.equals(includedRule)) {
				LOGGER.warn("'{}' is a legacy keyword, and should be replaced by {} and {}",
						JavaRefactorerProperties.WILDCARD,
						AllIncludingDraftCompositeMutators.class.getSimpleName(),
						AllIncludingDraftSingleMutators.class.getSimpleName());
				// We suppose there is no mutator from Composite which is not a single mutator
				// Hence we return all single mutators
				return compatibleSingleMutators.stream();
			} else {
				List<IMutator> matchingMutators =
						Stream.concat(compatibleSingleMutators.stream(), compatibleCompositeMutators.stream())
								.filter(someMutator -> isAcceptedMutator(includedRule, someMutator))
								.collect(Collectors.toList());

				if (!matchingMutators.isEmpty()) {
					return matchingMutators.stream();
				}

				var optFromClassName = loadMutatorFromClass(sourceCodeVersion, includedRule);

				if (optFromClassName.isPresent()) {
					return optFromClassName.stream();
				}

				if (allSingleIds.contains(includedRule) || allCompositeIds.contains(includedRule)) {
					LOGGER.warn(
							"includedMutator={} matches some mutators, but not compatible with sourceCodeVersion={}",
							includedRule,
							sourceCodeVersion);
				} else {
					LOGGER.warn(
							"includedMutator={} did not match any compatible mutator (sourceCodeVersion={}) singleIds={} compositeIds={}",
							includedRule,
							sourceCodeVersion,
							compatibleSingleIds,
							compatibleCompositeIds);
				}

				return Stream.empty();
			}
		}).collect(Collectors.toList());

		// We unroll composite to enable exclusion of included mutators
		var mutatorsNotComposite = unrollCompositeMutators(mutatorsMayComposite);

		// TODO '.distinct()' to handle multiple composites bringing the same mutator
		return mutatorsNotComposite.stream().filter(mutator -> {
			var isExcluded = excludedRules.contains(mutator.getClass().getName())
					|| excludedRules.stream().anyMatch(mutator.getIds()::contains);

			// debug as it seems Spotless instantiate this quite often / for each file
			if (isExcluded) {
				LOGGER.debug("We exclude {}->'{}'", mutator.getClass().getName(), mutator.getIds());
			} else {
				LOGGER.debug("We include {}->'{}'", mutator.getClass().getName(), mutator.getIds());
			}

			return !isExcluded;
		}).filter(ct -> {
			if (includeDraft) {
				return true;
			} else if (mutatorsMayComposite.contains(ct)) {
				LOGGER.debug("Draft are not included by default but {} was listed explicitely", ct.getIds());
				return true;
			} else {
				return !ct.isDraft();
			}
		}).collect(Collectors.toList());

	}

	private static boolean isAcceptedMutator(String includedRule, IMutator someMutator) {
		if (someMutator.getIds().contains(includedRule)) {
			return true;
		} else if (someMutator.getClass().getName().equals(includedRule)) {
			// We allow loading any rule, from a custom dependency
			return true;
		}
		return false;
	}

	private static Optional<IMutator> loadMutatorFromClass(JavaVersion sourceCodeVersion, String includedRule) {
		try {
			// https://www.baeldung.com/java-check-class-exists
			var classLoader = Thread.currentThread().getContextClassLoader();
			var mutatorClass = (Class<? extends IMutator>) Class.forName(includedRule, false, classLoader);

			IMutator mutator;
			if (CompositeMutator.class.isAssignableFrom(mutatorClass)) {
				var ctor = mutatorClass.getConstructor(JavaVersion.class);
				mutator = ctor.newInstance(sourceCodeVersion);
			} else {
				var ctor = mutatorClass.getConstructor();
				mutator = ctor.newInstance();
			}

			return Optional.of(mutator);
		} catch (ClassNotFoundException e) {
			LOGGER.debug("includedMutator {} is not present classname", includedRule, e);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("Unexpected constructor for includedMutator=" + includedRule, e);
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Invalid class for includedMutator=" + includedRule, e);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new IllegalArgumentException("Issue instanciating includedMutator=" + includedRule, e);
		}
		return Optional.empty();
	}

	private static List<IMutator> unrollCompositeMutators(List<IMutator> mutatorsMayComposite) {
		var mutatorsNotComposite = mutatorsMayComposite;

		// Iterate until all CompositeMutators has been unrolled
		while (mutatorsNotComposite.stream().anyMatch(CompositeMutator.class::isInstance)) {
			mutatorsNotComposite = mutatorsNotComposite.stream().flatMap(m -> {
				if (m instanceof CompositeMutator) {
					return ((CompositeMutator<?>) m).getUnderlyings().stream();
				} else {
					return Stream.<IMutator>of(m);
				}
			}).collect(Collectors.toList());
		}
		return mutatorsNotComposite;
	}

	protected abstract String toString(R walkResult);
}
