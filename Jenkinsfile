pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
    }

    environment {
        APP_NAME = 'demo-app'
        DB_CREDENTIALS_ID = 'mysql-db-creds'
        APP_PORT = '9090'
        DB_HOST = '127.0.0.1'
        DB_PORT = '3306'
        DB_NAME = 'nmmc'
        DEPLOY_DIR = 'D:\\Deployment\\demo'
        PID_FILE = 'D:\\Deployment\\demo\\demo-app.pid'
        LOG_OUT_FILE = 'D:\\Deployment\\demo\\demo-app.out.log'
        LOG_ERR_FILE = 'D:\\Deployment\\demo\\demo-app.err.log'
    }

    stages {
        stage('Checkout Source') {
            steps {
                checkout scm
            }
        }

        stage('Load DB Credentials') {
            steps {
                script {
                    if (!env.DB_USERNAME?.trim() || !env.DB_PASSWORD?.trim()) {
                        try {
                            withCredentials([usernamePassword(credentialsId: env.DB_CREDENTIALS_ID, usernameVariable: 'DB_USER_FROM_JENKINS', passwordVariable: 'DB_PASS_FROM_JENKINS')]) {
                                env.DB_USERNAME = env.DB_USER_FROM_JENKINS
                                env.DB_PASSWORD = env.DB_PASS_FROM_JENKINS
                            }
                            echo "Using DB credentials from Jenkins credentials ID: ${env.DB_CREDENTIALS_ID}"
                        } catch (Exception ignored) {
                            error("DB credentials missing. Set DB_USERNAME/DB_PASSWORD env vars, or create a Username/Password credential with ID '${env.DB_CREDENTIALS_ID}'.")
                        }
                    }
                }
            }
        }

        stage('Build JAR (Maven)') {
            steps {
                bat 'call mvn -B -ntp -DskipTests clean package'
            }
        }

        stage('Deploy Locally') {
            steps {
                powershell '''
                $ErrorActionPreference = "Stop"

                New-Item -Path $env:DEPLOY_DIR -ItemType Directory -Force | Out-Null

                $jarFile = Get-ChildItem -Path "$env:WORKSPACE\\target" -Filter "*.jar" -File |
                    Where-Object { $_.Name -notlike "*.original" } |
                    Sort-Object LastWriteTime -Descending |
                    Select-Object -First 1

                if (-not $jarFile) {
                    throw "No runnable JAR found in $env:WORKSPACE\\target"
                }

                if (Test-Path $env:PID_FILE) {
                    $oldPid = Get-Content $env:PID_FILE -ErrorAction SilentlyContinue
                    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
                        Stop-Process -Id $oldPid -Force
                        Start-Sleep -Seconds 2
                    }
                    Remove-Item $env:PID_FILE -Force -ErrorAction SilentlyContinue
                }

                $listener = Get-NetTCPConnection -LocalPort ([int]$env:APP_PORT) -State Listen -ErrorAction SilentlyContinue
                if ($listener) {
                    throw "Port $($env:APP_PORT) is already in use by PID $($listener.OwningProcess). Stop that process and retry."
                }

                $javaExe = $null
                if ($env:JAVA_HOME) {
                    $candidate = Join-Path $env:JAVA_HOME 'bin\\java.exe'
                    if (Test-Path $candidate) { $javaExe = $candidate }
                }
                if (-not $javaExe) {
                    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
                    if ($javaCmd) { $javaExe = $javaCmd.Source }
                }
                if (-not $javaExe) {
                    throw "Java not found. Set JAVA_HOME or ensure java is on PATH."
                }

                $env:DB_HOST = "$env:DB_HOST"
                $env:DB_PORT = "$env:DB_PORT"
                $env:DB_NAME = "$env:DB_NAME"
                $env:DB_USERNAME = "$env:DB_USERNAME"
                $env:DB_PASSWORD = "$env:DB_PASSWORD"
                $env:SERVER_PORT = "$env:APP_PORT"

                $proc = Start-Process -FilePath $javaExe `
                    -ArgumentList @("-jar", $jarFile.FullName) `
                    -WorkingDirectory $env:WORKSPACE `
                    -RedirectStandardOutput $env:LOG_OUT_FILE `
                    -RedirectStandardError $env:LOG_ERR_FILE `
                    -PassThru `
                    -WindowStyle Hidden

                Set-Content -Path $env:PID_FILE -Value $proc.Id -Encoding ascii
                Write-Host "Started $env:APP_NAME using JAR: $($jarFile.Name), PID: $($proc.Id)"
                '''
            }
        }

        stage('Verify Local Deployment') {
            steps {
                powershell '''
                $ErrorActionPreference = "Stop"

                if (-not (Test-Path $env:PID_FILE)) {
                    throw "PID file not found: $env:PID_FILE"
                }

                $appPid = (Get-Content $env:PID_FILE).Trim()
                $proc = Get-Process -Id $appPid -ErrorAction SilentlyContinue
                if (-not $proc) {
                    throw "Application process is not running (PID $appPid)."
                }

                for ($i = 0; $i -lt 30; $i++) {
                    $listener = Get-NetTCPConnection -LocalPort ([int]$env:APP_PORT) -State Listen -ErrorAction SilentlyContinue
                    if ($listener) {
                        Write-Host "Application is listening on port $env:APP_PORT (PID: $appPid)."
                        exit 0
                    }
                    Start-Sleep -Seconds 2
                }

                throw "Application did not start listening on port $env:APP_PORT within timeout."
                '''
            }
        }
    }
}
