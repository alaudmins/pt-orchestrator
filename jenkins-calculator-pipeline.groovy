pipeline {
    agent any
    
    parameters {
        choice(name: 'OPERATION', choices: ['ADD', 'SUBTRACT', 'MULTIPLY', 'DIVIDE'], description: 'Select the operation to perform')
        string(name: 'NUM1', defaultValue: '10', description: 'First number')
        string(name: 'NUM2', defaultValue: '5', description: 'Second number')
        booleanParam(name: 'VERBOSE', defaultValue: true, description: 'Enable verbose logging')
    }
    
    stages {
        stage('Validate Input') {
            steps {
                script {
                    echo "=========================================="
                    echo "Stage 1: Validate Input"
                    echo "=========================================="
                    echo "Operation: ${params.OPERATION}"
                    echo "Number 1: ${params.NUM1}"
                    echo "Number 2: ${params.NUM2}"
                    echo "Verbose: ${params.VERBOSE}"
                    
                    // Validate numbers
                    try {
                        def num1 = params.NUM1.toInteger()
                        def num2 = params.NUM2.toInteger()
                        echo "✅ Input validation successful"
                        echo "   Num1 (${num1}) is valid"
                        echo "   Num2 (${num2}) is valid"
                        
                        // Check for division by zero
                        if (params.OPERATION == 'DIVIDE' && num2 == 0) {
                            error "❌ Division by zero is not allowed!"
                        }
                        
                        // Store for next stage
                        env.VALIDATED_NUM1 = num1.toString()
                        env.VALIDATED_NUM2 = num2.toString()
                        
                    } catch (NumberFormatException e) {
                        error "❌ Invalid input: Numbers must be integers"
                    }
                }
            }
        }
        
        stage('Calculate Result') {
            steps {
                script {
                    echo "=========================================="
                    echo "Stage 2: Calculate Result"
                    echo "=========================================="
                    
                    def num1 = env.VALIDATED_NUM1.toInteger()
                    def num2 = env.VALIDATED_NUM2.toInteger()
                    def result
                    
                    echo "Performing operation: ${params.OPERATION}"
                    
                    switch(params.OPERATION) {
                        case 'ADD':
                            result = num1 + num2
                            echo "Calculating: ${num1} + ${num2}"
                            break
                        case 'SUBTRACT':
                            result = num1 - num2
                            echo "Calculating: ${num1} - ${num2}"
                            break
                        case 'MULTIPLY':
                            result = num1 * ${num2}"
                            break
                        case 'DIVIDE':
                            result = num1 / num2
                            echo "Calculating: ${num1} / ${num2}"
                            break
                        default:
                            error "❌ Unknown operation: ${params.OPERATION}"
                    }
                    
                    echo "=========================================="
                    echo "✅ CALCULATION COMPLETE"
                    echo "=========================================="
                    echo "Operation: ${params.OPERATION}"
                    echo "Input: ${num1} and ${num2}"
                    echo "Result: ${result}"
                    echo "=========================================="
                    
                    // Store result as environment variable
                    env.CALCULATION_RESULT = result.toString()
                    
                    if (params.VERBOSE) {
                        echo "Verbose mode enabled:"
                        echo "  - Build Number: ${env.BUILD_NUMBER}"
                        echo "  - Job Name: ${env.JOB_NAME}"
                        echo "  - Workspace: ${env.WORKSPACE}"
                        echo "  - Triggered by: pt-orchestrator"
                    }
                }
            }
        }
    }
    
    post {
        success {
            echo "=========================================="
            echo "🎉 Pipeline completed successfully!"
            echo "Final Result: ${env.CALCULATION_RESULT}"
            echo "=========================================="
        }
        failure {
            echo "=========================================="
            echo "❌ Pipeline failed!"
            echo "Check logs for error details"
            echo "=========================================="
        }
        always {
            echo "Pipeline execution finished at: ${new Date()}"
        }
    }
}
