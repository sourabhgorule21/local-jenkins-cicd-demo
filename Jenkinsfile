pipeline {
    agent any

    environment {
        DEPLOY_DIR = 'D:\\Deployment\\demo'
        BACKUP_DIR = 'D:\\Deployment\\demo\\backup'
        DEPLOY_JAR = 'myapp.jar'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build JAR') {
            steps {
                bat 'call mvn clean package -DskipTests'
            }
        }

        stage('Backup Old JAR') {
            steps {
                powershell '''
                $ErrorActionPreference = "Stop"

                New-Item -Path $env:DEPLOY_DIR -ItemType Directory -Force | Out-Null
                New-Item -Path $env:BACKUP_DIR -ItemType Directory -Force | Out-Null

                $currentJar = Join-Path $env:DEPLOY_DIR $env:DEPLOY_JAR
                if (Test-Path $currentJar) {
                    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
                    $backupJar = Join-Path $env:BACKUP_DIR ("myapp_" + $timestamp + ".jar")
                    Move-Item -Path $currentJar -Destination $backupJar -Force
                    Write-Host "Old JAR backed up to: $backupJar"
                } else {
                    Write-Host "No existing JAR found to back up."
                }
                '''
            }
        }

        stage('Deploy New JAR') {
            steps {
                powershell '''
                $ErrorActionPreference = "Stop"

                $newJar = Get-ChildItem -Path "$env:WORKSPACE\\target" -Filter "*.jar" -File |
                    Where-Object { $_.Name -notlike "*.original" } |
                    Sort-Object LastWriteTime -Descending |
                    Select-Object -First 1

                if (-not $newJar) {
                    throw "No JAR file found in $env:WORKSPACE\\target"
                }

                $destination = Join-Path $env:DEPLOY_DIR $env:DEPLOY_JAR
                Copy-Item -Path $newJar.FullName -Destination $destination -Force
                Write-Host "Deployed JAR to: $destination"
                '''
            }
        }

        stage('Run Application') {
            steps {
                bat '''
                if not exist "D:\\Deployment\\demo\\run.bat" (
                    echo run.bat not found in D:\\Deployment\\demo
                    exit /b 1
                )
                call "D:\\Deployment\\demo\\run.bat"
                '''
            }
        }
    }
}
