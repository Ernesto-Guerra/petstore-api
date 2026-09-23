package com.example.exam.client;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class PetStoreClientConfiguration {

	@Bean
	RestClient petStoreRestClient(RestClient.Builder builder, PetStoreProperties properties) {
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
			.withConnectTimeout(properties.connectTimeout())
			.withReadTimeout(properties.readTimeout());
		return builder.baseUrl(properties.baseUrl())
			.requestFactory(ClientHttpRequestFactories.get(settings))
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
	}

}
