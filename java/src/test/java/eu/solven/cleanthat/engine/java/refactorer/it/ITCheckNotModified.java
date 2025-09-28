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
package eu.solven.cleanthat.engine.java.refactorer.it;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.junit.Test;

import com.github.javaparser.StaticJavaParser;
import com.google.common.collect.ImmutableMap;

import lombok.extern.slf4j.Slf4j;

// TODO Move this to somewhere not specific to lambda?
// https://github.com/javaparser/javaparser/issues/3317
@Slf4j
public class ITCheckNotModified {

	protected Map<?, ?> ShouldNotMutate() {
		// Before
		return ImmutableMap.builder()
				// Middle Comment
				.build();
		// After
	}

	@Test
	public void testUnmodifiedString() throws IOException {
		// JavaParser.setDoNotAssignCommentsPreceedingEmptyLines(true);
		LOGGER.info("Process: {}");

		var file = new File("src/test/java/" + ITCheckNotModified.class.getName().replace('.', '/') + ".java");

		if (!file.isFile()) {
			throw new IllegalArgumentException("Can not read: " + file.getAbsolutePath());
		}

		var pathAsString = Files.readString(file.toPath());

		var node = StaticJavaParser.parse(pathAsString);

		// IClassTransformer rule = new VariableEqualsConstant();
		// boolean changed = rule.walkNode(node);
		//
		// if (!changed) {
		// throw new IllegalArgumentException(rule + " did not change: " + file.getAbsolutePath());
		// }

		DiffMatchPatch dmp = new DiffMatchPatch();
		var newAsString = node.toString();

		LOGGER.info("new:");
		LOGGER.info("----NEW--------START-------------");
		LOGGER.info(newAsString);
		LOGGER.info("----NEW---------END--------------");

		// TODO We may need to reformat to have a nice diff
		List<DiffMatchPatch.Diff> diff = dmp.diffMain(pathAsString, newAsString, false);
		diff.forEach(d -> LOGGER.info("{}", d));
	}
}
