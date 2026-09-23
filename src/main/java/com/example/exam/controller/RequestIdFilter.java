package com.example.exam.controller;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlaciona las líneas de bitácora de cada petición mediante un identificador que también se
 * devuelve al consumidor. El que propone el cliente solo se acepta si su formato es seguro de
 * escribir en la bitácora.
 */
@Component
class RequestIdFilter extends OncePerRequestFilter {

	static final String REQUEST_ID_HEADER = "X-Request-Id";

	static final String REQUEST_ID_MDC_KEY = "requestId";

	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
		MDC.put(REQUEST_ID_MDC_KEY, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove(REQUEST_ID_MDC_KEY);
		}
	}

	private static String resolveRequestId(String candidate) {
		if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
			return candidate;
		}
		return UUID.randomUUID().toString();
	}

}
