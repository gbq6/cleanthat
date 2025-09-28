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
package eu.solven.cleanthat.engine.java.refactorer.mutators;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BinaryExpr.Operator;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.types.ResolvedType;
import com.google.common.collect.ImmutableSet;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaparserNodeMutator;
import eu.solven.cleanthat.engine.java.refactorer.NodeAndSymbolSolver;
import eu.solven.cleanthat.engine.java.refactorer.helpers.MethodCallExprHelpers;
import lombok.extern.slf4j.Slf4j;

/**
 * cases inspired from #description
 *
 * @author Sébastien Collard
 */
@Slf4j
public class CreateTempFilesUsingNio extends AJavaparserNodeMutator {

	@Override
	public String minimalJavaVersion() {
		// java.nio.Files has been introduced in JDK7
		return IJdkVersionConstants.JDK_7;
	}

	@Override
	public Set<String> getTags() {
		return ImmutableSet.of("NIO");
	}

	@Override
	public Optional<String> getSonarId() {
		return Optional.of("RSPEC-2976");
	}

	@Override
	public String jSparrowUrl() {
		return "https://jsparrow.github.io/rules/create-temp-files-using-java-nio.html";
	}

	@Override
	public String getId() {
		return "CreateTempFilesUsingNio";
	}

	@Override
	protected boolean processNotRecursively(NodeAndSymbolSolver<?> node) {
		// ResolvedMethodDeclaration test;
		if (!(node.getNode() instanceof MethodCallExpr)) {
			return false;
		}
		var methodCallExpr = (MethodCallExpr) node.getNode();
		if (!"createTempFile".equals(methodCallExpr.getName().getIdentifier())) {
			return false;
		}
		Optional<Boolean> optIsStatic = mayStaticCall(methodCallExpr);
		if (optIsStatic.isPresent() && !optIsStatic.get()) {
			return false;
		}
		Optional<Expression> optScope = methodCallExpr.getScope();
		if (optScope.isPresent()) {
			Optional<ResolvedType> type = MethodCallExprHelpers.optResolvedType(node.editNode(optScope));
			if (type.isEmpty() || !"java.io.File".equals(type.get().asReferenceType().getQualifiedName())) {
				return false;
			}
			LOGGER.debug("Found : {}", node);
			if (process(methodCallExpr)) {
				return true;
			}
		}
		return false;
	}

	private Optional<Boolean> mayStaticCall(MethodCallExpr methodCallExpr) {
		try {
			return Optional.of(methodCallExpr.resolve().isStatic());
		} catch (Exception e) {
			// Knowing if class is static requires a SymbolResolver:
			// 'java.lang.IllegalStateException: Symbol resolution not configured: to configure consider setting a
			// SymbolResolver in the ParserConfiguration'
			LOGGER.debug("arg", e);
			return Optional.empty();
		}
	}

	private boolean process(MethodCallExpr methodExp) {
		List<Expression> arguments = methodExp.getArguments();
		Optional<Expression> optToPath;
		var newStaticClass = new NameExpr("Files");
		var newStaticMethod = "createTempFile";
		var minArgSize = 2;
		if (arguments.size() == minArgSize) {
			// Create in default tmp directory
			LOGGER.debug("Add java.nio.file.Files to import");
			methodExp.tryAddImportToParentCompilationUnit(Files.class);
			optToPath = Optional.of(new MethodCallExpr(newStaticClass, newStaticMethod, methodExp.getArguments()));
		} else if (arguments.size() == minArgSize + 1) {
			var arg0 = methodExp.getArgument(0);
			var arg1 = methodExp.getArgument(1);
			var arg3 = methodExp.getArgument(2);
			if (arg3.isObjectCreationExpr()) {
				methodExp.tryAddImportToParentCompilationUnit(Paths.class);
				var objectCreation = (ObjectCreationExpr) methodExp.getArgument(minArgSize);
				NodeList<Expression> objectCreationArguments = objectCreation.getArguments();
				NodeList<Expression> replaceArguments =
						new NodeList<>(new MethodCallExpr(new NameExpr("Paths"), "get", objectCreationArguments),
								arg0,
								arg1);
				optToPath = Optional.of(new MethodCallExpr(newStaticClass, newStaticMethod, replaceArguments));
			} else if (arg3.isNameExpr()) {
				// The directory may be null, in which case case, we'll rely on the default tmp directory
				var fileIsNull = new BinaryExpr(arg3, new NullLiteralExpr(), Operator.EQUALS);

				NodeList<Expression> replaceArgumentsIfNull = new NodeList<>(arg0, arg1);
				var callIfNull = new MethodCallExpr(newStaticClass, newStaticMethod, replaceArgumentsIfNull);

				NodeList<Expression> replaceArgumentsNotNull =
						new NodeList<>(new MethodCallExpr(arg3, "toPath"), arg0, arg1);
				var callNotNull = new MethodCallExpr(newStaticClass, newStaticMethod, replaceArgumentsNotNull);

				// We need to enclose the ternary between '(...)' as we will call .toFile() right-away
				Expression enclosedTernary = new EnclosedExpr(new ConditionalExpr(fileIsNull, callIfNull, callNotNull));

				optToPath = Optional.of(enclosedTernary);
			} else if (arg3.isNullLiteralExpr()) {
				// 'null' is managed specifically as Files.createTempFile does not accept a null as directory
				NodeList<Expression> replaceArguments = new NodeList<>(arg0, arg1);
				optToPath = Optional.of(new MethodCallExpr(newStaticClass, newStaticMethod, replaceArguments));
			} else {
				optToPath = Optional.empty();
			}
		} else {
			optToPath = Optional.empty();
		}
		optToPath.ifPresent(toPath -> {
			methodExp.tryAddImportToParentCompilationUnit(Files.class);
			tryReplace(methodExp, new MethodCallExpr(toPath, "toFile"));
		});
		return optToPath.isPresent();
	}
}
