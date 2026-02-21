pipeline {
    agent any

    parameters {
        string(name: 'LABEL',          defaultValue: 'Job A',  description: 'Display label for this job')
        string(name: 'SLEEP_DURATION', defaultValue: '15',     description: 'Seconds to sleep (simulates work)')
        string(name: 'OUTPUT_MESSAGE', defaultValue: 'Hello from Job A', description: 'Message to echo')
    }

    stages {
        stage('Start') {
            steps {
                script {
                    echo "=========================================="
                    echo "🚀 ${params.LABEL} — Starting"
                    echo "=========================================="
                    echo "Sleep Duration : ${params.SLEEP_DURATION}s"
                    echo "Output Message : ${params.OUTPUT_MESSAGE}"
                    echo "Build Number   : ${env.BUILD_NUMBER}"
                    echo "Triggered by   : pt-orchestrator"
                }
            }
        }

        stage('Work') {
            steps {
                script {
                    def sleepSec = params.SLEEP_DURATION.toInteger()
                    echo "💤 ${params.LABEL} — Sleeping ${sleepSec}s to simulate work..."
                    sleep(time: sleepSec, unit: 'SECONDS')
                    echo "✅ ${params.LABEL} — Work complete"
                }
            }
        }

        stage('Output') {
            steps {
                script {
                    echo "=========================================="
                    echo "📢 ${params.OUTPUT_MESSAGE}"
                    echo "🎉 ${params.LABEL} — Finished successfully!"
                    echo "=========================================="
                }
            }
        }
    }

    post {
        success { echo "✅ ${params.LABEL} — Pipeline SUCCESS" }
        failure { echo "❌ ${params.LABEL} — Pipeline FAILED" }
        always  { echo "Pipeline finished at: ${new Date()}" }
    }
}
