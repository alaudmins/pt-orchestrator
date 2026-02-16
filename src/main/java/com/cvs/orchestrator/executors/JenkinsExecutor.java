package com.cvs.orchestrator.executors;

import com.cvs.orchestrator.model.runtime.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JenkinsExecutor implements StepExecutor {

    @Override
    public String getType() {
        return "JENKINS_JOB";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        Map<String, Object> metadata = context.getMetadata() != null ? context.getMetadata() : new HashMap<>();

        String jenkinsUrl = (String) config.get("jenkinsUrl");
        String jobName = (String) config.get("jobName");
        String username = (String) config.get("username");
        String token = (String) config.get("token");

        // Check if already triggered
        if (metadata.containsKey("buildNumber")) {
            // Poll for status
            return checkBuildStatus(jenkinsUrl, jobName, username, token, metadata);
        } else if (metadata.containsKey("queueUrl")) {
            // Check queue for build number
            return checkQueue(jenkinsUrl, jobName, username, token, metadata);
        } else {
            // Trigger new build
            return triggerBuild(jenkinsUrl, jobName, username, token, config);
        }
    }

    private StepExecutionResult triggerBuild(String jenkinsUrl, String jobName, String username, String token,
            Map<String, Object> config) {
        log.info("Triggering Jenkins job: {}", jobName);

        // TODO: Implement actual Jenkins API call
        // POST {jenkinsUrl}/job/{jobName}/buildWithParameters
        // Extract queue URL from Location header

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("queueUrl", jenkinsUrl + "/queue/item/123"); // Mock
        metadata.put("triggeredAt", System.currentTimeMillis());

        return StepExecutionResult.running(metadata);
    }

    private StepExecutionResult checkQueue(String jenkinsUrl, String jobName, String username, String token,
            Map<String, Object> metadata) {
        log.info("Checking Jenkins queue for job: {}", jobName);

        // TODO: Implement actual queue check
        // GET {queueUrl}/api/json
        // Check for "executable": { "number": 123 }

        // Mock: Assume build number is ready
        metadata.put("buildNumber", 123);
        metadata.remove("queueUrl");

        return StepExecutionResult.running(metadata);
    }

    private StepExecutionResult checkBuildStatus(String jenkinsUrl, String jobName, String username, String token,
            Map<String, Object> metadata) {
        Integer buildNumber = (Integer) metadata.get("buildNumber");
        log.info("Checking Jenkins build status: {}/{}", jobName, buildNumber);

        // TODO: Implement actual build status check
        // GET {jenkinsUrl}/job/{jobName}/{buildNumber}/api/json
        // Check "building": false, "result": "SUCCESS"

        // Mock: Return success immediately
        Map result = new HashMap();
        result.put("building", false);
        result.put("result", "SUCCESS");

        if ((Boolean) result.get("building")) {
            return StepExecutionResult.running(metadata);
        }

        String jenkinsResult = (String) result.get("result");
        if ("SUCCESS".equals(jenkinsResult)) {
            return StepExecutionResult.success();
        } else {
            return StepExecutionResult.failed("Jenkins build failed: " + jenkinsResult);
        }
    }
}
