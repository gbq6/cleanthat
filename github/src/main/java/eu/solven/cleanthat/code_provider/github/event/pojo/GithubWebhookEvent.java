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
package eu.solven.cleanthat.code_provider.github.event.pojo;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.Strings;

import eu.solven.cleanthat.lambda.step0_checkwebhook.I3rdPartyWebhookEvent;
import eu.solven.cleanthat.lambda.step0_checkwebhook.IWebhookEvent;
import eu.solven.pepper.collection.PepperMapHelper;
import lombok.extern.slf4j.Slf4j;

/**
 * a POJO holding details about Github webhooks
 * 
 * @author Benoit Lacelle
 *
 */
@Slf4j
public class GithubWebhookEvent implements I3rdPartyWebhookEvent {

	public static final String KEY_BODY = "body";
	public static final String KEY_HEADERS = "headers";

	@Deprecated
	public static final String KEY_GITHUB = "github";

	final String xGithubEvent;
	final String xGithubDelivery;
	final String xHubSignature256;

	final Map<String, ?> body;

	public GithubWebhookEvent(String xGithubEvent,
			String xGithubDelivery,
			String xGithubSignature256,
			Map<String, ?> body) {
		this.xGithubEvent = xGithubEvent;
		this.xGithubDelivery = xGithubDelivery;
		this.xHubSignature256 = xGithubSignature256;
		this.body = body;
	}

	public GithubWebhookEvent(Map<String, ?> body) {
		this.xGithubEvent = "";
		this.xGithubDelivery = "";
		this.xHubSignature256 = "";
		this.body = body;
	}

	public String getxGithubEvent() {
		return xGithubEvent;
	}

	public String getxGithubDelivery() {
		return xGithubDelivery;
	}

	public String getxHubSignature256() {
		return xHubSignature256;
	}

	@Override
	public Map<String, ?> getBody() {
		return body;
	}

	@Override
	public Map<String, ?> getHeaders() {
		Map<String, Object> headers = new LinkedHashMap<>();

		if (!Strings.isNullOrEmpty(xGithubEvent)) {
			headers.put("X-GitHub-Event", xGithubEvent);
		}
		if (!Strings.isNullOrEmpty(xGithubDelivery)) {
			headers.put(X_GIT_HUB_DELIVERY, xGithubDelivery);
		}
		if (!Strings.isNullOrEmpty(xHubSignature256)) {
			headers.put("X-Hub-Signature-256", xHubSignature256);
		}

		return headers;
	}

	public static GithubWebhookEvent fromCleanThatEvent(IWebhookEvent githubAcceptedEvent) {
		if (githubAcceptedEvent instanceof GithubWebhookEvent) {
			return (GithubWebhookEvent) githubAcceptedEvent;
		} else if (githubAcceptedEvent instanceof CleanThatWebhookEvent) {
			Map<String, ?> cleanthatBody = githubAcceptedEvent.getBody();

			Map<String, ?> githubBodyAndHeaders;
			if (cleanthatBody.containsKey(GithubWebhookEvent.KEY_GITHUB)) {
				LOGGER.warn("Processing a legacy input: {}", githubAcceptedEvent);
				githubBodyAndHeaders = PepperMapHelper.getRequiredMap(cleanthatBody, GithubWebhookEvent.KEY_GITHUB);
			} else {
				githubBodyAndHeaders = cleanthatBody;
			}

			Map<Object, ?> githubHeaders = PepperMapHelper.getRequiredMap(githubBodyAndHeaders, KEY_HEADERS);

			var xGithubEvent = PepperMapHelper.getOptionalString(githubHeaders, "X-GitHub-Event").orElse("");
			var xGithubDelivery = PepperMapHelper.getOptionalString(githubHeaders, X_GIT_HUB_DELIVERY).orElse("");
			var xGithubSignature256 =
					PepperMapHelper.getOptionalString(githubHeaders, "X-GitHub-Signature-256").orElse("");

			return new GithubWebhookEvent(xGithubEvent,
					xGithubDelivery,
					xGithubSignature256,
					PepperMapHelper.getRequiredMap(githubBodyAndHeaders, KEY_BODY));
		} else {
			throw new IllegalArgumentException("What is this? body=" + githubAcceptedEvent.getBody());
		}

	}
}
