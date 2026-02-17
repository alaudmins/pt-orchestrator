package com.cvs.orchestrator.util;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for resolving environment variable placeholders in
 * configuration values.
 * Supports patterns like ${ENV_VAR_NAME} and ${ENV_VAR_NAME:default_value}
 */
@Slf4j
public class EnvVarResolver {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    /**
     * Resolves environment variable placeholders in a string value.
     * 
     * @param value String that may contain ${VAR_NAME} or ${VAR_NAME:default}
     *              patterns
     * @return Resolved string with environment variables substituted
     * @throws IllegalArgumentException if a required env var is missing (no default
     *                                  provided)
     */
    public static String resolve(String value) {
        if (value == null) {
            return null;
        }

        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);

            String envValue = System.getenv(varName);

            if (envValue != null) {
                log.debug("Resolved env var ${{{}}}: {}", varName,
                        envValue.substring(0, Math.min(10, envValue.length())) + "...");
                matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
            } else if (defaultValue != null) {
                log.debug("Using default value for ${{{}}}: {}", varName, defaultValue);
                matcher.appendReplacement(result, Matcher.quoteReplacement(defaultValue));
            } else {
                throw new IllegalArgumentException(
                        "Environment variable '" + varName + "' is required but not set. " +
                                "Please set it or provide a default value in the configuration.");
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Resolves environment variables in all string values within a Map recursively.
     * 
     * @param config Configuration map that may contain string values with env var
     *               placeholders
     * @return New map with all environment variables resolved
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveMap(Map<String, Object> config) {
        if (config == null) {
            return null;
        }

        Map<String, Object> resolved = new HashMap<>();

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof String) {
                resolved.put(entry.getKey(), resolve((String) value));
            } else if (value instanceof Map) {
                resolved.put(entry.getKey(), resolveMap((Map<String, Object>) value));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }

        return resolved;
    }
}
