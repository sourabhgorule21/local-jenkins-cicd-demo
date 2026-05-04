pipeline {
    agent any

    environment {
        DEPLOY_DIR = 'D:\\Deployment\\demo'
        BACKUP_DIR = 'D:\\Deployment\\demo\\backup'
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

        stage('Stop Running App') {
            steps {
                powershell '''
                $ErrorActionPreference = "Stop"

                $appPort = 9090
                Write-Host "Checking running process on port $appPort..."

                $portPids = netstat -ano | Select-String ":$appPort\\s+.*LISTENING" | ForEach-Object {
                    ($_ -split "\\s+")[-1]
                } | Where-Object { $_ -match "^[0-9]+$" } | Sort-Object -Unique

                foreach ($procId in $portPids) {
                    Write-Host "Stopping PID $procId listening on port $appPort"
                    Stop-Process -Id ([int]$procId) -Force -ErrorAction SilentlyContinue
                }

                $jarPids = Get-CimInstance Win32_Process |
                    Where-Object {
                        $_.Name -eq "java.exe" -and
                        $_.CommandLine -like "*D:\\Deployment\\demo\\*.jar*"
                    } |
                    Select-Object -ExpandProperty ProcessId -Unique

                foreach ($procId in $jarPids) {
                    Write-Host "Stopping deployment java PID $procId"
                    Stop-Process -Id ([int]$procId) -Force -ErrorAction SilentlyContinue
                }
                '''
            }
        }

        stage('Backup Old JAR') {
            steps {
                powershell '''
                $ErrorActionPreference = "Stop"

                New-Item -Path $env:DEPLOY_DIR -ItemType Directory -Force | Out-Null
                New-Item -Path $env:BACKUP_DIR -ItemType Directory -Force | Out-Null

                $existingJars = Get-ChildItem -Path $env:DEPLOY_DIR -Filter "*.jar" -File -ErrorAction SilentlyContinue
                if ($existingJars) {
                    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
                    foreach ($jar in $existingJars) {
                        $backupJar = Join-Path $env:BACKUP_DIR ($jar.BaseName + "_" + $timestamp + $jar.Extension)
                        Copy-Item -Path $jar.FullName -Destination $backupJar -Force
                        Write-Host "Old JAR backed up to: $backupJar"
                    }
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

                $destination = Join-Path $env:DEPLOY_DIR $newJar.Name
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

