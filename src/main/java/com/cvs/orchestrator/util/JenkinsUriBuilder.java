package com.cvs.orchestrator.util;

public class JenkinsUriBuilder {

    /**
     * Constructs a full Jenkins job absolute URL given a base Jenkins instance URL
     * and a job name path.
     * Safely handles context paths (e.g. ci-autoeng.cvshealth.com/jenkins) and
     * prevents
     * stripping of the context path by HTTP clients.
     *
     * @param baseUrl The base URL of the Jenkins instance (e.g.
     *                "https://jenkins.company.com/jenkins")
     * @param jobName The raw Jenkins job name/path (e.g. "My-Folder/My-Job")
     * @return The absolute URL to the Jenkins job (e.g.
     *         "https://jenkins.company.com/jenkins/job/My-Folder/job/My-Job")
     */
    public static String buildAbsoluteJobUrl(String baseUrl, String jobName) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Jenkins baseUrl must not be blank");
        }
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("Jenkins jobName must not be blank");
        }

        String cleanBaseUrl = baseUrl.trim();
        if (cleanBaseUrl.endsWith("/")) {
            cleanBaseUrl = cleanBaseUrl.substring(0, cleanBaseUrl.length() - 1);
        }

        String[] parts = jobName.trim().split("/");
        StringBuilder jobPathFragment = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                jobPathFragment.append("/job/").append(trimmed);
            }
        }

        return cleanBaseUrl + jobPathFragment.toString();
    }
}
