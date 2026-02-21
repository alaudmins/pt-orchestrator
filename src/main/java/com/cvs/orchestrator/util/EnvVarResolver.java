package com.cvs.orchestrator.util;

import com.cvs.orchestrator.service.SecretService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves configuration placeholders at execution time:
 *
 * ${ENV_VAR} → system environment variable
 * ${ENV_VAR:default} → env var with fallback
 * secret:name → decrypted value from the built-in secrets store
 *
 * The secrets store lookup is the preferred production pattern.
 * Users POST secrets once via POST /api/secrets and reference them in YAMLs:
 *
 * token: "secret:my-github-token"
 *
 * No container restarts or env var changes needed when rotating credentials.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnvVarResolver {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    private static final String SECRET_PREFIX = "secret:";

    private final SecretService secretService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolve a single string value.
     * Handles:
     * "secret:token-name" → secrets store lookup
     * "${ENV_VAR}" → environment variable
     * "${ENV_VAR:default}" → environment variable with default
     * anything else → returned as-is
     */
    public String resolve(String value) {
        if (value == null)
            return null;

        // Check for secret: prefix first (entire string is a secret reference)
        if (value.startsWith(SECRET_PREFIX)) {
            String name = value.substring(SECRET_PREFIX.length()).trim();
            log.debug("Resolving secret reference: secret:{}", name);
            return secretService.getSecretValue(name);
        }

        // Fall back to ${ENV_VAR} / ${ENV_VAR:default} substitution
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);
            String envValue = System.getenv(varName);

            if (envValue != null) {
                log.debug("Resolved env var ${{{}}}", varName);
                matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
            } else if (defaultValue != null) {
                log.debug("Using default for ${{{}}}", varName);
                matcher.appendReplacement(result, Matcher.quoteReplacement(defaultValue));
            } else {
                throw new IllegalArgumentException(
                        "Environment variable '" + varName + "' is not set and has no default. " +
                                "Tip: store it securely with POST /api/secrets and reference as: secret:"
                                + varName.toLowerCase());
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Recursively resolve all string values in a configuration map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveMap(Map<String, Object> config) {
        if (config == null)
            return null;

        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                resolved.put(entry.getKey(), resolve(s));
            } else if (value instanceof Map) {
                resolved.put(entry.getKey(), resolveMap((Map<String, Object>) value));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }
}
