package com.cvs.orchestrator.model.definition;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RetryPolicy {
    private int maxAttempts = 1;
    private long backoffSeconds = 0;
    private boolean exponential = false;
}
