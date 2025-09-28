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
package eu.solven.cleanthat.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import eu.solven.cleanthat.config.pojo.SourceCodeProperties;
import eu.solven.cleanthat.formatter.LineEnding;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestConfigHelpers {

	private static final String EOL = System.lineSeparator();

	@Test
	public void testYaml() throws JsonParseException, JsonMappingException, IOException {
		var yamlObjectMapper = ConfigHelpers.makeYamlObjectMapper();
		var asString = yamlObjectMapper.writeValueAsString(Map.of("k1", Map.of("k2", "v")));
		// This may demonstrate unexpected behavior with EOL on different systems
		Assertions.assertThat(asString).contains("k", "  k2: \"v\"");
		Assertions.assertThat(asString.split(EOL)).hasSize(2);
	}

	@Ignore("We do not manipulate .json configuration anymore")
	@Test
	public void testFromJsonToYaml() throws JsonParseException, JsonMappingException, IOException {
		var jsonObjectMapper = ConfigHelpers.makeJsonObjectMapper();
		var configHelpers = new ConfigHelpers(Arrays.asList(jsonObjectMapper));
		// 'default_as_json' case is not satisfying as we have null in its yaml version
		Stream.of("simple_as_json", "default_as_json").forEach(name -> {
			try {
				var config = configHelpers.loadRepoConfig(new ClassPathResource("/config/" + name + ".json"));
				var yamlObjectMapper = ConfigHelpers.makeYamlObjectMapper();
				var asYaml = yamlObjectMapper.writeValueAsString(config);
				LOGGER.debug("Config as yaml: {}{}{}{}{}{}", EOL, "------", EOL, asYaml, EOL, "------");
				var expectedYaml = StreamUtils.copyToString(
						new ClassPathResource("/config/" + name + ".to_yaml.yaml").getInputStream(),
						StandardCharsets.UTF_8);
				if ("\r\n".equals(EOL)) {
					// We are seemingly under Windows
					if (!expectedYaml.contains(EOL)) {
						Assert.fail("Files are not checked-out with system EOL");
					} else if (!asYaml.contains(EOL)) {
						Assert.fail("YAML are not generated with system EOL");
					}
				}
				Assert.assertEquals("Issue with " + name, expectedYaml, asYaml);
			} catch (IOException e) {
				throw new UncheckedIOException("Issue with: " + name, e);
			}
		});
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMergeSourceCodeEol() {
		var om = ConfigHelpers.makeYamlObjectMapper();
		var helper = new ConfigHelpers(Collections.singleton(om));

		var defaultP = SourceCodeProperties.defaultRoot();
		defaultP.setEncoding(StandardCharsets.ISO_8859_1.name());
		var windowsP = SourceCodeProperties.builder().lineEnding(LineEnding.CRLF).build();
		windowsP.setEncoding(StandardCharsets.US_ASCII.name());

		Assert.assertEquals(LineEnding.GIT, defaultP.getLineEndingAsEnum());
		Assert.assertEquals(LineEnding.CRLF, windowsP.getLineEndingAsEnum());

		Assert.assertEquals("ISO-8859-1", defaultP.getEncoding());
		Assert.assertEquals("US-ASCII", windowsP.getEncoding());

		// windows as inner
		{
			var mergedAsMap = helper.mergeSourceCodeProperties(om.convertValue(defaultP, Map.class),
					om.convertValue(windowsP, Map.class));
			var merged = om.convertValue(mergedAsMap, SourceCodeProperties.class);

			Assert.assertEquals(LineEnding.CRLF, merged.getLineEndingAsEnum());
			Assert.assertEquals("US-ASCII", merged.getEncoding());
		}

		// windows as outer
		{
			var mergedAsMap = helper.mergeSourceCodeProperties(om.convertValue(windowsP, Map.class),
					om.convertValue(defaultP, Map.class));
			var merged = om.convertValue(mergedAsMap, SourceCodeProperties.class);

			Assert.assertEquals(LineEnding.GIT, merged.getLineEndingAsEnum());
			Assert.assertEquals("ISO-8859-1", merged.getEncoding());
		}

		// default and default
		{
			var mergedAsMap = helper.mergeSourceCodeProperties(om.convertValue(defaultP, Map.class),
					om.convertValue(defaultP, Map.class));
			var merged = om.convertValue(mergedAsMap, SourceCodeProperties.class);

			Assert.assertEquals(LineEnding.GIT, merged.getLineEndingAsEnum());
			Assert.assertEquals("ISO-8859-1", merged.getEncoding());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMergeSourceCode_parentHasExcluded() {
		var om = ConfigHelpers.makeYamlObjectMapper();
		var helper = new ConfigHelpers(Collections.singleton(om));

		var defaultP = SourceCodeProperties.builder().exclude(".*/generated/.*").build();

		{
			// EmptyChildren
			var childrenP = SourceCodeProperties.builder().build();

			var mergedAsMap = helper.mergeSourceCodeProperties(om.convertValue(defaultP, Map.class),
					om.convertValue(childrenP, Map.class));
			var merged = om.convertValue(mergedAsMap, SourceCodeProperties.class);

			Assert.assertEquals(Arrays.asList(".*/generated/.*"), merged.getExcludes());
		}

		{
			// NotEmptyChildren
			var childrenP = SourceCodeProperties.builder().exclude(".*/do_not_clean_me/.*").build();

			var mergedAsMap = helper.mergeSourceCodeProperties(om.convertValue(defaultP, Map.class),
					om.convertValue(childrenP, Map.class));
			var merged = om.convertValue(mergedAsMap, SourceCodeProperties.class);

			Assert.assertEquals(Arrays.asList(".*/generated/.*", ".*/do_not_clean_me/.*"), merged.getExcludes());
		}

		{
			// NotEmptyChildren and cancel parent exclusion
			var childrenP =
					SourceCodeProperties.builder().exclude(".*/do_not_clean_me/.*").include(".*/generated/.*").build();

			var mergedAsMap = helper.mergeSourceCodeProperties(om.convertValue(defaultP, Map.class),
					om.convertValue(childrenP, Map.class));
			var merged = om.convertValue(mergedAsMap, SourceCodeProperties.class);

			Assert.assertEquals(Arrays.asList(".*/do_not_clean_me/.*"), merged.getExcludes());
			Assert.assertEquals(Arrays.asList(".*/generated/.*"), merged.getIncludes());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMergeSourceCode_parentHasIncluded() {
		var om = ConfigHelpers.makeYamlObjectMapper();
		var helper = new ConfigHelpers(Collections.singleton(om));

		var defaultP = SourceCodeProperties.builder().include(".*\\.xml").build();

		{
			// EmptyChildren
			var childrenP = SourceCodeProperties.builder().build();

			var mergedAsMap = helper.mergeSourceCodeProperties(om.convertValue(defaultP, Map.class),
					om.convertValue(childrenP, Map.class));
			var merged = om.convertValue(mergedAsMap, SourceCodeProperties.class);

			Assert.assertEquals(Arrays.asList(".*\\.xml"), merged.getIncludes());
		}

		{
			// NotEmptyChildren
			var childrenP = SourceCodeProperties.builder().include("pom.xml").build();

			var mergedAsMap = helper.mergeSourceCodeProperties(om.convertValue(defaultP, Map.class),
					om.convertValue(childrenP, Map.class));
			var merged = om.convertValue(mergedAsMap, SourceCodeProperties.class);

			Assert.assertEquals(Arrays.asList("pom.xml"), merged.getIncludes());
		}
	}
}
