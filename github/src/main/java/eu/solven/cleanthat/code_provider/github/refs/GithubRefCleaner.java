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
package eu.solven.cleanthat.code_provider.github.refs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.github.GHCheckRunBuilder.Output;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHCompare.Commit;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GHUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import eu.solven.cleanthat.any_language.ACodeCleaner;
import eu.solven.cleanthat.code_provider.github.GithubHelper;
import eu.solven.cleanthat.code_provider.github.event.GithubAndToken;
import eu.solven.cleanthat.code_provider.github.event.GithubCheckRunManager;
import eu.solven.cleanthat.code_provider.github.refs.all_files.GithubBranchCodeReadWriter;
import eu.solven.cleanthat.code_provider.github.refs.all_files.GithubRefCodeProvider;
import eu.solven.cleanthat.code_provider.github.refs.all_files.GithubRefCodeReadWriter;
import eu.solven.cleanthat.codeprovider.CodeProviderDecoratingWriter;
import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.codeprovider.ICodeProviderWriter;
import eu.solven.cleanthat.codeprovider.decorator.IGitBranch;
import eu.solven.cleanthat.codeprovider.decorator.IGitCommit;
import eu.solven.cleanthat.codeprovider.decorator.IGitRepository;
import eu.solven.cleanthat.codeprovider.decorator.ILazyGitReference;
import eu.solven.cleanthat.codeprovider.git.GitRepoBranchSha1;
import eu.solven.cleanthat.codeprovider.git.HeadAndOptionalBase;
import eu.solven.cleanthat.codeprovider.git.IExternalWebhookRelevancyResult;
import eu.solven.cleanthat.codeprovider.git.IGitRefCleaner;
import eu.solven.cleanthat.config.ICleanthatConfigConstants;
import eu.solven.cleanthat.config.ICleanthatConfigInitializer;
import eu.solven.cleanthat.config.IDocumentationConstants;
import eu.solven.cleanthat.config.RepoInitializerResult;
import eu.solven.cleanthat.config.pojo.CleanthatRefFilterProperties;
import eu.solven.cleanthat.config.pojo.CleanthatRepositoryProperties;
import eu.solven.cleanthat.formatter.CodeFormatResult;
import eu.solven.cleanthat.formatter.ICodeProviderFormatter;
import eu.solven.cleanthat.git_abstraction.GithubFacade;
import eu.solven.cleanthat.git_abstraction.GithubRepositoryFacade;
import eu.solven.cleanthat.github.ICleanthatGitRefsConstants;
import eu.solven.cleanthat.github.IGitRefsConstants;
import eu.solven.cleanthat.utils.ResultOrError;
import lombok.extern.slf4j.Slf4j;

/**
 * Default for {@link IGitRefCleaner}
 *
 * @author Benoit Lacelle
 */
@Slf4j
@SuppressWarnings("PMD.GodClass")
public class GithubRefCleaner extends ACodeCleaner implements IGitRefCleaner, ICleanthatGitRefsConstants {

	public static final String ENV_TEMPORARY_BRANCH_SUFFIX = "cleanthat.temporary_branch_suffix";

	final GithubAndToken githubAndToken;

	final GithubCheckRunManager githubCheckRunManager;

	public GithubRefCleaner(List<ObjectMapper> objectMappers,
			ICleanthatConfigInitializer configInitializer,
			ICodeProviderFormatter formatterProvider,
			GithubAndToken githubAndToken,
			GithubCheckRunManager githubCheckRunManager) {
		super(objectMappers, configInitializer, formatterProvider);

		this.githubAndToken = githubAndToken;

		this.githubCheckRunManager = githubCheckRunManager;
	}

	protected String temporaryBranchSuffix() {
		var suffixFromEnv = System.getProperty(ENV_TEMPORARY_BRANCH_SUFFIX);
		if (!Strings.isNullOrEmpty(suffixFromEnv)) {
			return suffixFromEnv;
		}
		return LocalDate.now().toString();
	}

	// We may have no ref to clean (e.g. there is no cleanthat configuration, or the ref is excluded)
	// We may have to clean current ref (e.g. a PR is open, and we want to clean the PR head)
	// We may have to clean a different ref (e.g. a push to the main branch needs to be cleaned through a PR)
	// @SuppressWarnings("PMD.CognitiveComplexity")
	@Override
	public Optional<HeadAndOptionalBase> prepareRefToClean(Path root,
			String eventKey,
			IExternalWebhookRelevancyResult result,
			GitRepoBranchSha1 head,
			// There can be multiple eventBaseBranches in case of push events
			Set<String> eventBaseRefs) {
		var codeProvider = getCodeProviderForRef(root, head);
		ResultOrError<CleanthatRepositoryProperties, String> optConfig;
		try {
			optConfig = loadAndCheckConfiguration(codeProvider);
		} catch (RuntimeException e) {
			updateCheckRunFailureWithConfig(eventKey, head);

			throw new RuntimeException(e);
		}

		if (optConfig.getOptError().isPresent()) {
			updateCheckRunFailureWithConfig(eventKey, head);

			return Optional.empty();
		}

		var properties = optConfig.getOptResult().get();

		// TODO If the configuration changed, trigger full-clean only if the change is an effective change (and not just
		// json/yaml/etc formatting)
		migrateConfigurationCode(properties);

		var headRef = head.getRef();
		{
			var excludedPatterns = properties.getMeta().getRefs().getExcludedPatterns();
			var optHeadMatchingRule = selectPatternOfSensibleHead(excludedPatterns, headRef);
			if (optHeadMatchingRule.isPresent()) {
				LOGGER.info("We skip this event as head={} is excluded by {}", headRef, optHeadMatchingRule.get());
				return Optional.empty();
			}

			if (selectNotMatchingBase(eventBaseRefs, excludedPatterns).isEmpty()) {
				LOGGER.info("We skip this event as base={} is excluded by {}", eventBaseRefs, excludedPatterns);
				return Optional.empty();
			}
		}

		var protectedPatterns = properties.getMeta().getRefs().getProtectedPatterns();

		if (canCleanInPlace(eventBaseRefs, protectedPatterns, headRef)) {
			logWhyCanCleanInPlace(eventBaseRefs, protectedPatterns, result, headRef);

			// TODO We should take as base the base from 'canCleanInPlace'
			// This is especially important in pushes after a rr-open, as the push before the rr-open would not be
			// cleaned
			// It would also help workaround previous clean having failed (e.g. by cleaning the RR on each event, not
			// just the commits of the latest push)
			return cleanHeadInPlace(result, head);
		}

		if (canCleanInNewRR(protectedPatterns, headRef)) {
			return cleanInNewRR(result, head, protectedPatterns, headRef);
		} else {
			// Cleanable neither in-place (e.g. protected branch) nor in-rr
			LOGGER.info("This branch seems not cleanable: {}. Regex: {}. eventBaseBranches: {}",
					headRef,
					protectedPatterns,
					eventBaseRefs);
			return Optional.empty();
		}
	}

	private void updateCheckRunFailureWithConfig(String eventKey, GitRepoBranchSha1 head) {
		GHRepository repository;
		var repoName = head.getRepoFullName();
		try {
			repository = githubAndToken.getGithub().getRepository(repoName);
		} catch (IOException e) {
			throw new UncheckedIOException("Issue fetching repository: " + repoName, e);
		}
		githubCheckRunManager.createCheckRun(githubAndToken, repository, head.getSha(), eventKey).ifPresent(cr -> {
			try {
				List<String> summuaryRows = ImmutableList.<String>builder()
						.add("Check " + ICleanthatConfigConstants.DEFAULT_PATH_CLEANTHAT)
						.add("It may look like: " + IDocumentationConstants.URL_REPO
								+ "/blob/master/runnable/src/test/resources/config/default-safe.yaml")
						.build();

				var summary = summuaryRows.stream().collect(Collectors.joining("\r\n"));
				cr.update()
						.withConclusion(Conclusion.ACTION_REQUIRED)
						.withStatus(Status.COMPLETED)
						.add(new Output("Issue with configuration", summary))
						.create();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	protected Optional<HeadAndOptionalBase> cleanInNewRR(IExternalWebhookRelevancyResult result,
			GitRepoBranchSha1 head,
			List<String> cleanableRefsRegexes,
			String headRef) {
		// We'll have to open a dedicated ReviewRequest if necessary
		// As we prefer not to pollute a random existing PR
		var newBranchRef = prepareRefNameForHead(headRef);
		// We may open a branch later if it appears this branch is relevant
		// BEWARE we do not open the branch right now: we wait to detect at least one fail is relevant to be clean
		// In case of concurrent events, we may end opening multiple PR to clean the same branch
		LOGGER.info(
				"ref={} is not cleanable in-place, but cleanable in-rr. Commits would be pushed into {} (cleanableRegex={})",
				headRef,
				newBranchRef,
				cleanableRefsRegexes);

		// See GithubEventHelper.doOpenPr(WebhookRelevancyResult, GithubRepositoryFacade, GitRepoBranchSha1)
		var optBaseRef = result.optBaseRef();
		if (optBaseRef.isEmpty()) {
			// This may happen on the event of branch creation, when the branch is cleanable
			throw new IllegalStateException("No baseRef? headRef=" + headRef);
		}

		var base = optBaseRef.get();
		// We keep the base sha1, as it will be used for diff computations (i.e. listing the concerned files)
		// We use as refName the pushedRef/rrHead as it is to this ref that a RR has to be open
		var actualBase = new GitRepoBranchSha1(base.getRepoFullName(), head.getRef(), base.getSha());

		var actualHead = new GitRepoBranchSha1(head.getRepoFullName(), newBranchRef, head.getSha());
		return Optional.of(new HeadAndOptionalBase(actualHead, Optional.of(actualBase)));
	}

	private boolean canCleanInNewRR(List<String> cleanableRefsRegexes, String headRef) {
		var optHeadMatchingRule = selectPatternOfSensibleHead(cleanableRefsRegexes, headRef);

		return optHeadMatchingRule.isPresent();
	}

	private boolean canCleanInPlace(Set<String> eventBaseRefs, List<String> protectedPatterns, String headRef) {
		var optHeadMatchingRule = selectPatternOfSensibleHead(protectedPatterns, headRef);
		if (optHeadMatchingRule.isPresent()) {
			// We never clean in place the cleanable branches, as they are considered sensible
			LOGGER.info("Not cleaning in-place as head={} is a sensible/cleanable ref (rule={})",
					headRef,
					optHeadMatchingRule.get());
			return false;
		}

		var optBaseMatchingRule = selectMatchingBase(eventBaseRefs, protectedPatterns);

		return optBaseMatchingRule.isPresent();
	}

	// https://github.com/pmd/pmd/issues?q=is%3Aissue+is%3Aopen+InvalidLogMessageFormat
	@SuppressWarnings("PMD.InvalidLogMessageFormat")
	private void logWhyCanCleanInPlace(Set<String> eventBaseRefs,
			List<String> refToCleanRegexes,
			IExternalWebhookRelevancyResult result,
			String headRef) {
		var optBaseMatchingRule = selectMatchingBase(eventBaseRefs, refToCleanRegexes);

		if (optBaseMatchingRule.isEmpty()) {
			throw new IllegalStateException("Should be called only if .canCleanInPlace() returns true");
		}

		// TODO We should ensure the HEAD does not match any regex (i.e. not clean in-place a sensible branch, even
		// if it is the head of a RR)
		var baseMatchingRule = optBaseMatchingRule.get();

		var prefix = "Cleaning {} in-place as ";
		var suffix = " a sensible/cleanable base (rule={})";
		if (result.isReviewRequestOpen()) {
			LOGGER.info(prefix + "RR has" + suffix, headRef, baseMatchingRule);
		} else {
			LOGGER.info(prefix + "pushed used as head of a RR with" + suffix, headRef, baseMatchingRule);
		}
	}

	private Optional<String> selectPatternOfSensibleHead(List<String> protectedPatterns, String fullRef) {
		return protectedPatterns.stream().filter(regex -> Pattern.matches(regex, fullRef)).findAny();
	}

	private boolean hasMatch(Set<String> refs, String regex) {
		var matchingBase = refs.stream().filter(base -> Pattern.matches(regex, base)).findAny();

		if (matchingBase.isEmpty()) {
			LOGGER.info("Not a single base with open RR matches cleanableBranchRegex={}", regex);
			return false;
		} else {
			LOGGER.info("We have a match for ruleBranch={} eventBaseBranch={}", regex, matchingBase.get());
			return true;
		}
	}

	/**
	 * 
	 * @param baseRefs
	 *            eligible full refs
	 * @param refPatterns
	 *            the regex of the branches allowed to be clean. Fact is these branches should never be cleaned by
	 *            themselves, but only through RR
	 * @return
	 */
	private Optional<String> selectMatchingBase(Set<String> baseRefs, List<String> refPatterns) {
		return refPatterns.stream().filter(regex -> hasMatch(baseRefs, regex)).findAny();
	}

	private Optional<String> selectNotMatchingBase(Set<String> baseRefs, List<String> refPatterns) {
		return refPatterns.stream().filter(regex -> !hasMatch(baseRefs, regex)).findAny();
	}

	private Optional<HeadAndOptionalBase> cleanHeadInPlace(IExternalWebhookRelevancyResult result,
			GitRepoBranchSha1 head) {
		// The base is cleanable: we are allowed to clean its head in-place
		var optBase = result.optBaseRef();
		if (optBase.isPresent()) {
			var base = optBase.get();

			if (IGitRefsConstants.SHA1_CLEANTHAT_UP_TO_REF_ROOT.equals(base.getSha())) {
				// Typically a refs has been created, or forced-push
				// Its base would be the ancestor commit which is in the default branch
				try {
					var baseRepoFullName = base.getRepoFullName();
					GHRepository repo = githubAndToken.getGithub().getRepository(baseRepoFullName);

					// BEWARE What-if head is a commit of the defaultBranch?
					GHBranch defaultBranch = GithubHelper.getDefaultBranch(repo);

					// https://docs.github.com/en/rest/commits/commits#compare-two-commits
					GHCompare compare = repo.getCompare(defaultBranch.getSHA1(), head.getSha());
					Commit mergeBaseCommit = compare.getMergeBaseCommit();

					var newBase = new GitRepoBranchSha1(baseRepoFullName, base.getRef(), mergeBaseCommit.getSHA1());
					LOGGER.info("We will use as base={} to clean head={}", newBase, head);
					optBase = Optional.of(newBase);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		}

		return Optional.of(new HeadAndOptionalBase(head, optBase));
	}

	/**
	 * @return a new/unique reference, useful when opening a branch to clean a cleanable branch.
	 */
	public String prepareRefNameForHead(String baseToClean) {
		// We do not want to open a ref on every event, so we should not hand a random suffix.
		// We do not want to resurrect previous branch each time a head has to be created, so we need a suffix
		// We now suggest opening a RR at most once per day. We may not open it if there is a previous day RR still open
		var nowSuffix = "-" + temporaryBranchSuffix();

		var ref = PREFIX_REF_CLEANTHAT_TMPHEAD + baseToClean.replace('/', '_').replace('-', '_') + nowSuffix;
		LOGGER.info("We provisioned a (temporary, not-materialized yet) head branch={} to clean base branch={}",
				ref,
				baseToClean);
		return ref;
	}

	public ICodeProvider getCodeProviderForRef(Path root, GitRepoBranchSha1 theRef) {
		var ref = theRef.getRef();

		try {
			var repoName = theRef.getRepoFullName();
			GithubFacade facade = new GithubFacade(githubAndToken.getGithub(), repoName);
			GHRef refObject = facade.getRef(ref);
			return new GithubRefCodeProvider(root, githubAndToken.getToken(), facade.getRepository(), refObject);
		} catch (IOException e) {
			throw new UncheckedIOException("Issue with ref: " + ref, e);
		}
	}

	private CodeFormatResult formatRefDiff(String eventKey,
			GHRepository theRepo,
			ICodeProvider codeProvider,
			ILazyGitReference headSupplier) {
		ICodeProviderWriter codeProviderWriter = new CodeProviderDecoratingWriter(codeProvider, () -> {
			// Get the head lazily, to prevent creating empty branches
			GHRef headWhereToWrite = headSupplier.getSupplier().get().getDecorated();
			var repositoryRoot = codeProvider.getRepositoryRoot();
			return new GithubRefCodeReadWriter(repositoryRoot,
					githubAndToken.getToken(),
					eventKey,
					theRepo,
					headWhereToWrite,
					headSupplier.getFullRefOrSha1());
		});
		return formatCodeGivenConfig(eventKey, codeProviderWriter, false);
	}

	// @Deprecated(
	// since = "We clean on push events. This would be used on open PR events, but we may still fallback on sha1 diff
	// cleaning")
	// @Override
	// public CodeFormatResult formatRefDiff(Path root,
	// IGitRepository repo,
	// IGitReference base,
	// ILazyGitReference headSupplier) {
	// String refOrSha1 = headSupplier.getFullRefOrSha1();
	// GHRef ghBase = base.getDecorated();
	//
	// LOGGER.info("Base: {} Head: {}", ghBase.getRef(), refOrSha1);
	// GHRepository theRepo = repo.getDecorated();
	// String token = githubAndToken.getToken();
	// GHCommit head = new GithubRepositoryFacade(theRepo).getCommit(refOrSha1);
	// ICodeProvider codeProvider =
	// new GithubRefToCommitDiffCodeProvider(root.getFileSystem(), token, theRepo, ghBase, head);
	// return formatRefDiff(theRepo, codeProvider, headSupplier);
	// }

	@Override
	public CodeFormatResult formatCommitToRefDiff(Path root,
			String eventKey,
			IGitRepository repo,
			IGitCommit base,
			ILazyGitReference headSupplier) {
		var refOrSha1 = headSupplier.getFullRefOrSha1();
		GHCommit ghBase = base.getDecorated();

		LOGGER.info("Base: {} Head: {}", ghBase.getSHA1(), refOrSha1);
		GHRepository theRepo = repo.getDecorated();
		String token = githubAndToken.getToken();
		GHCommit head = new GithubRepositoryFacade(theRepo).getCommit(refOrSha1);
		ICodeProvider codeProvider = new GithubCommitToCommitDiffCodeProvider(root, token, theRepo, ghBase, head);
		// Typically used to load the configuration
		// ICodeProvider headCodeProvider = new GithubCommitToCommitDiffCodeProvider(token, theRepo, ghBase, head);
		return formatRefDiff(eventKey, theRepo, codeProvider, headSupplier);
	}

	@Override
	public CodeFormatResult formatRef(Path root,
			String eventKey,
			IGitRepository repo,
			IGitBranch branchSupplier,
			ILazyGitReference headSupplier) {
		GHBranch branch = branchSupplier.getDecorated();

		ICodeProviderWriter codeProvider =
				new GithubBranchCodeReadWriter(root, githubAndToken.getToken(), eventKey, repo.getDecorated(), branch);
		return formatRefDiff(eventKey, repo.getDecorated(), codeProvider, headSupplier);
	}

	@Override
	public boolean tryOpenPRWithCleanThatStandardConfiguration(String eventKey, Path root, IGitBranch branch) {
		GHBranch defaultBranch = branch.getDecorated();
		GHRepository repo = defaultBranch.getOwner();

		String branchName = defaultBranch.getName();
		var baseRef = CleanthatRefFilterProperties.BRANCHES_PREFIX + branchName;
		var codeProvider = getCodeProviderForRef(root,
				new GitRepoBranchSha1(repo.getFullName(), baseRef, defaultBranch.getSHA1()));
		Optional<Map<String, ?>> optPrConfig = safeConfig(codeProvider);
		if (optPrConfig.isPresent()) {
			LOGGER.info("There is a configuration (valid or not) in the default branch ({})", branchName);
			return false;
		} else {
			LOGGER.info("There is no configuration in the default branch ({})", branchName);
		}

		try {
			closeOldConfigurePr(repo);
		} catch (RuntimeException e) {
			LOGGER.warn("New feature is failing", e);
		}

		var headRef = REF_NAME_CONFIGURE;
		Optional<GHRef> optRefToPR = optRef(repo, headRef);
		try {
			if (optRefToPR.isPresent()) {
				GHRef refToPr = optRefToPR.get();
				LOGGER.info("There is already a ref preparing cleanthat integration. Do not open a new PR (url={})",
						refToPr.getUrl().toExternalForm());
				repo.listPullRequests(GHIssueState.ALL).forEach(pr -> {
					if (headRef.equals(pr.getHead().getRef())) {
						LOGGER.info("Related PR: {}", pr.getHtmlUrl());
					}
				});
				return false;
			} else {
				RepoInitializerResult result = generateDefaultConfiguration(codeProvider, repo.isPrivate(), eventKey);

				GHCommit commit = commitConfig(defaultBranch, repo, result);
				GHRef refToPr = repo.createRef(headRef, commit.getSHA1());
				var force = false;
				refToPr.updateTo(commit.getSHA1(), force);

				// Issue using '/' in the base, while renovate succeed naming branches: 'renovate/configure'
				// TODO What is this issue exactly? We seem to success naming our ref 'cleanthat/configure'
				GHPullRequest pr = repo.createPullRequest("Configure CleanThat",
						refToPr.getRef(),
						baseRef,
						result.getPrBody(),
						true,
						false);
				LOGGER.info("Open PR: {}", pr.getHtmlUrl());
				return true;
			}
		} catch (IOException e) {
			// TODO If 401, it probably means the Installation is not allowed to modify given repo
			throw new UncheckedIOException(e);
		}
	}

	private Optional<GHRef> optRef(GHRepository repo, String headRef) {
		Optional<GHRef> optRefToPR;
		try {
			try {
				optRefToPR = Optional.of(new GithubRepositoryFacade(repo).getRef(headRef));
				LOGGER.info("There is already a ref: " + headRef);
			} catch (GHFileNotFoundException e) {
				LOGGER.trace("There is not yet a ref: " + headRef, e);
				LOGGER.info("There is not yet a ref: " + headRef);
				optRefToPR = Optional.empty();
			}
		} catch (IOException e) {
			// TODO If 401, it probably means the Installation is not allowed to see/modify given repository
			throw new UncheckedIOException(e);
		}
		return optRefToPR;
	}

	private void closeOldConfigurePr(GHRepository repo) {
		var headRef = REF_NAME_CONFIGURE_V1;
		Optional<GHRef> optRefToPR = optRef(repo, headRef);
		if (optRefToPR.isPresent()) {
			GHRef refToPr = optRefToPR.get();
			LOGGER.info("There is already a ref preparing cleanthat integration. Do not open a new PR (url={})",
					refToPr.getUrl().toExternalForm());
			repo.listPullRequests(GHIssueState.OPEN).forEach(pr -> {
				GHCommitPointer prHead = pr.getHead();
				try {
					if (headRef.equals(prHead.getRef()) && GithubHelper.isCleanthatAuthor(pr.getUser())) {
						GHUser author = prHead.getCommit().getAuthor();
						String headAuthorName = author.getName();
						if (GithubHelper.isCleanthatAuthor(author)) {
							LOGGER.info("Closing old 'configure' PR: {}", pr.getHtmlUrl());
						} else {
							LOGGER.warn("Leaving open old 'configure' PR: {} as head.authorName={}",
									pr.getHtmlUrl(),
									headAuthorName);
						}
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	private GHCommit commitConfig(GHBranch defaultBranch, GHRepository repo, RepoInitializerResult result)
			throws IOException {
		GHTreeBuilder baseTreeBuilder = GithubRefWriterLogic.prepareBuilderTree(repo, result.getPathToContents())
				.baseTree(defaultBranch.getSHA1());

		GHTree createTree = baseTreeBuilder.create();
		GHCommit commit = GithubRefWriterLogic.prepareCommit(repo)
				.message(result.getCommitMessage())
				.parent(defaultBranch.getSHA1())
				.tree(createTree.getSha())
				.create();
		return commit;
	}

}
