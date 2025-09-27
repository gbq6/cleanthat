package eu.solven.cleanthat.engine.java.refactorer.cases.do_not_format_me;

import java.io.IOException;
import java.util.HashMap;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;

import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.engine.java.refactorer.annotations.CaseNotYetImplemented;
import eu.solven.cleanthat.engine.java.refactorer.annotations.CompareMethods;
import eu.solven.cleanthat.engine.java.refactorer.annotations.UnmodifiedInnerClass;
import eu.solven.cleanthat.engine.java.refactorer.meta.IJavaparserAstMutator;
import eu.solven.cleanthat.engine.java.refactorer.mutators.EmptyControlStatement;
import eu.solven.cleanthat.engine.java.refactorer.test.AJavaparserRefactorerCases;

public class TestEmptyControlStatementCases extends AJavaparserRefactorerCases {
	@Override
	public IJavaparserAstMutator getTransformer() {
		return new EmptyControlStatement();
	}

	// We keep the 'if' statements as they may call methods, hence having side-effects
	@CompareMethods
	public static class VariousCases {
		public void pre() {
			// empty if statement
			if (true)
				;

			// empty as well
			if (true) {
			}

			{

			}

			{
				{

					{

					}
				}
			}
		}

		public void post() {
			// empty if statement
			if (true)
				;

			// empty as well
			if (true) {
			}

		}
	}

	// In edgy-cases, one would consider this as a feature (e.g. if one wants a custom class)
	@Ignore("This may deserve a dedicated mutator")
	// https://pmd.github.io/pmd/pmd_rules_java_bestpractices.html#doublebraceinitialization
	@CompareMethods
	public static class AnonymousClass {
		public Object pre() {
			return new HashMap<>() {

			};
		}

		public Object post() {
			return new HashMap<>();
		}
	}

	@CompareMethods
	public static class AnonymousClass_EmptyInitializer {
		public Object pre() {
			return new HashMap<>() {
				{

				}

				@Override
				public Object get(Object key) {
					return super.get(key);
				}

			};
		}

		public Object post() {
			return new HashMap<>() {
				@Override
				public Object get(Object key) {
					return super.get(key);
				}

			};
		}
	}

	@CompareMethods
	public static class AnonymousClass_EmptyInitializer_RecursiveEmpty {
		public Object pre() {
			return new HashMap<>() {
				{
					{

					}
				}

				@Override
				public Object get(Object key) {
					return super.get(key);
				}

			};
		}

		public Object post() {
			return new HashMap<>() {
				@Override
				public Object get(Object key) {
					return super.get(key);
				}

			};
		}
	}

	@CompareMethods
	@CaseNotYetImplemented
	public static class InLambda {
		public void pre(ICodeProvider cp) throws IOException {
			cp.listFilesForContent(file -> {
				Assertions.fail("The FS is empty");
			});
		}

		public void post(ICodeProvider cp) throws IOException {
			cp.listFilesForContent(file -> {
				Assertions.fail("The FS is empty");
			});
		}
	}

	@UnmodifiedInnerClass
	public static class EmptyMethod {

		public static class Pre {
			public void testArrayInt() {

			}
		}
	}

	@UnmodifiedInnerClass
	public static class WithComment {

		public static class Pre {
			public void testArrayInt() {
				{
					// Some comment
				}

				// Comment1
				;
				// Comment2
				;
				;
			}
		}

		public static class Post {
			public void testArrayInt() {

			}
		}
	}
}
