pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        // ===== UPDATE THESE VALUES =====
        // Final deployed JAR file name inside DEPLOY_DIR
        APP_JAR_NAME = 'jenkins-cicd-demo-1.0.0.jar'

        // Java executable used to run the app
        JAVA_EXE = 'C:\\Program Files\\Java\\jdk-21\\bin\\java.exe'
        // ===============================

        DEPLOY_DIR = 'D:\\Deployment\\demo'
        BACKUP_DIR = 'D:\\Deployment\\demo\\backup'
        LOG_DIR    = 'D:\\Deployment\\demo\\logs'
        PID_FILE   = 'D:\\Deployment\\demo\\app.pid'
    }

    stages {
        stage('Checkout Source') {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage('Build JAR (Maven)') {
            steps {
                bat '''
                    @echo on
                    call mvn -B -ntp -DskipTests clean package
                '''
            }
        }

        stage('Deploy To Windows Server') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"

                    try {
                        $deployDir = $env:DEPLOY_DIR
                        $backupDir = $env:BACKUP_DIR
                        $logDir = $env:LOG_DIR
                        $pidFile = $env:PID_FILE
                        $jarName = $env:APP_JAR_NAME
                        $javaExe = $env:JAVA_EXE

                        if ([string]::IsNullOrWhiteSpace($jarName)) {
                            throw "APP_JAR_NAME is empty. Set APP_JAR_NAME in Jenkinsfile."
                        }
                        if (-not (Test-Path -LiteralPath $javaExe)) {
                            throw "JAVA_EXE not found: $javaExe"
                        }

                        New-Item -ItemType Directory -Force -Path $deployDir | Out-Null
                        New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
                        New-Item -ItemType Directory -Force -Path $logDir | Out-Null

                        $builtJar = Join-Path -Path $pwd -ChildPath ("target\\" + $jarName)
                        if (-not (Test-Path -LiteralPath $builtJar)) {
                            throw "Built JAR not found: $builtJar. Verify APP_JAR_NAME matches target output."
                        }

                        $deployedJar = Join-Path -Path $deployDir -ChildPath $jarName

                        # 1) Stop process using PID file (fast path)
                        if (Test-Path -LiteralPath $pidFile) {
                            $existingPidRaw = (Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
                            if ($existingPidRaw -and $existingPidRaw -match '^\\d+$') {
                                $existingPid = [int]$existingPidRaw
                                $procByPid = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
                                if ($procByPid) {
                                    Write-Host "Stopping existing process from PID file: $existingPid"
                                    Stop-Process -Id $existingPid -Force -ErrorAction Stop
                                    Start-Sleep -Seconds 2
                                }
                            }
                            Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
                        }

                        # 2) Stop any java process whose command line contains the JAR name (fallback)
                        $jarNamePattern = [Regex]::Escape($jarName)
                        $javaProcs = Get-CimInstance -ClassName Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
                            Where-Object { $_.CommandLine -and $_.CommandLine -match $jarNamePattern }

                        foreach ($p in $javaProcs) {
                            Write-Host "Stopping Java process by JAR name match: PID $($p.ProcessId)"
                            Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
                        }

                        Start-Sleep -Seconds 1

                        # Backup old JAR with timestamp before replacing
                        if (Test-Path -LiteralPath $deployedJar) {
                            $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
                            $baseName = [System.IO.Path]::GetFileNameWithoutExtension($jarName)
                            $backupJar = Join-Path -Path $backupDir -ChildPath ($baseName + "-" + $timestamp + ".jar")
                            Move-Item -LiteralPath $deployedJar -Destination $backupJar -Force
                            Write-Host "Backup created: $backupJar"
                        } else {
                            Write-Host "No existing deployed JAR found in $deployDir. Skipping backup move."
                        }

                        # Copy new artifact to deployment directory
                        Copy-Item -LiteralPath $builtJar -Destination $deployedJar -Force
                        Write-Host "Copied new JAR to: $deployedJar"

                        # Start new JAR as background process with logs
                        $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
                        $outLog = Join-Path -Path $logDir -ChildPath ("app-" + $timestamp + ".out.log")
                        $errLog = Join-Path -Path $logDir -ChildPath ("app-" + $timestamp + ".err.log")

                        $proc = Start-Process -FilePath $javaExe `
                            -WorkingDirectory $deployDir `
                            -ArgumentList @("-jar", $deployedJar) `
                            -RedirectStandardOutput $outLog `
                            -RedirectStandardError $errLog `
                            -PassThru `
                            -WindowStyle Hidden

                        if (-not $proc -or -not $proc.Id) {
                            throw "Failed to start the new JAR process."
                        }

                        $proc.Id | Out-File -LiteralPath $pidFile -Encoding ascii -Force
                        Write-Host "Application started. PID: $($proc.Id)"
                        Write-Host "StdOut: $outLog"
                        Write-Host "StdErr: $errLog"
                    }
                    catch {
                        Write-Error "Deployment script failed: $($_.Exception.Message)"
                        throw
                    }
                '''
            }
        }
    }

    post {
        success {
            echo "Deployment completed successfully."
        }
        failure {
            echo "Deployment failed. Check stage logs for root cause."
        }
        always {
            echo "Branch: ${env.BRANCH_NAME ?: 'Configured in Jenkins job SCM'}"
            echo "Deploy Dir: ${env.DEPLOY_DIR}"
            echo "Backup Dir: ${env.BACKUP_DIR}"
        }
    }
}
