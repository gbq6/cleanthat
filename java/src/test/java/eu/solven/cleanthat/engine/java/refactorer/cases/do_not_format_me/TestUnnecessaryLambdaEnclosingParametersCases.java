package eu.solven.cleanthat.engine.java.refactorer.cases.do_not_format_me;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Ignore;

import eu.solven.cleanthat.engine.java.refactorer.annotations.CompareMethods;
import eu.solven.cleanthat.engine.java.refactorer.annotations.UnmodifiedMethod;
import eu.solven.cleanthat.engine.java.refactorer.meta.IJavaparserAstMutator;
import eu.solven.cleanthat.engine.java.refactorer.mutators.UnnecessaryLambdaEnclosingParameters;
import eu.solven.cleanthat.engine.java.refactorer.test.AJavaparserRefactorerCases;

public class TestUnnecessaryLambdaEnclosingParametersCases extends AJavaparserRefactorerCases {

	@Override
	public IJavaparserAstMutator getTransformer() {
		return new UnnecessaryLambdaEnclosingParameters();
	}

	@UnmodifiedMethod
	public static class CaseRunnable {
		public Runnable pre() {
			return () -> System.out.print("ok");
		}
	}

	@Ignore("JavaParser seems to ignore `setEnclosingParameters(false)`")
	@CompareMethods
	public static class CaseFunction {
		public Function<Integer, Integer> pre() {
			return (a) -> a % 5;
		}

		public Function<Integer, Integer> post() {
			return a -> a % 5;
		}
	}

	@UnmodifiedMethod
	public static class CaseTypedFunction {
		public Function<Integer, Integer> pre() {
			return (Integer a) -> a % 5;
		}
	}

	@UnmodifiedMethod
	public static class CaseBiFunction {
		public BiFunction<Integer, Integer, Integer> pre() {
			return (a, b) -> a + b;
		}
	}

	@UnmodifiedMethod
	public static class CaseTypedBiFunction {
		public BiFunction<Integer, Integer, Integer> pre() {
			return (Integer a, Integer b) -> a + b;
		}
	}

}
