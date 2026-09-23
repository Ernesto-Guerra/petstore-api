package com.example.exam;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los valores que devuelve la tienda terminan en la bitácora, así que el patrón de consola que se
 * despliega no debe permitir que un consumidor falsifique entradas insertando saltos de línea.
 */
class ConsoleLogPatternTest {

	private static final String FORGED_MESSAGE = "name=malicioso\n2024-06-25 19:22:42.000  INFO  entrada falsificada";

	@Test
	void theConsolePatternKeepsEveryRecordOnASingleLine() throws Exception {
		String rendered = render(consolePatternOf("application.yml"), FORGED_MESSAGE);

		assertThat(rendered).endsWith(System.lineSeparator())
			.satisfies(line -> assertThat(line.stripTrailing()).doesNotContain("\n").doesNotContain("\r"))
			.contains("entrada falsificada");
	}

	private static String consolePatternOf(String resource) throws Exception {
		PropertySource<?> propertySource = new YamlPropertySourceLoader()
			.load("application", new ClassPathResource(resource))
			.get(0);
		Object pattern = propertySource.getProperty("logging.pattern.console");
		assertThat(pattern).as("logging.pattern.console in %s", resource).isNotNull();
		return pattern.toString();
	}

	private static String render(String pattern, String message) {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		PatternLayout layout = new PatternLayout();
		layout.setContext(context);
		layout.setPattern(pattern);
		layout.start();
		try {
			LoggingEvent event = new LoggingEvent();
			event.setLoggerContext(context);
			event.setLoggerName(ConsoleLogPatternTest.class.getName());
			event.setLevel(Level.INFO);
			event.setMessage(message);
			event.setTimeStamp(System.currentTimeMillis());
			return layout.doLayout(event);
		}
		finally {
			layout.stop();
		}
	}

}
