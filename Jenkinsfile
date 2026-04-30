pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
    }

    environment {
        APP_NAME = 'demo-app'
        APP_PORT = '9090'
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

                $healthUrl = "http://127.0.0.1:$($env:APP_PORT)/actuator/health"
                $maxWaitSec = 120
                $sleepSec = 2
                $sw = [System.Diagnostics.Stopwatch]::StartNew()
                $lastError = $null

                while ($sw.Elapsed.TotalSeconds -lt $maxWaitSec) {
                    $running = Get-Process -Id $appPid -ErrorAction SilentlyContinue
                    if (-not $running) {
                        Write-Host "Application process exited before becoming ready."
                        if (Test-Path $env:LOG_ERR_FILE) { Get-Content $env:LOG_ERR_FILE -Tail 80 }
                        if (Test-Path $env:LOG_OUT_FILE) { Get-Content $env:LOG_OUT_FILE -Tail 80 }
                        throw "Application process is not running (PID $appPid)."
                    }

                    try {
                        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 5
                        if ($response.StatusCode -eq 200 -and $response.Content -match '"status"\\s*:\\s*"UP"') {
                            Write-Host "Application is ready on $healthUrl (PID: $appPid)."
                            exit 0
                        }
                    } catch {
                        $lastError = $_.Exception.Message
                    }

                    $elapsed = [int]$sw.Elapsed.TotalSeconds
                    Write-Host "Waiting for app readiness... ${elapsed}s elapsed."
                    Start-Sleep -Seconds $sleepSec
                }

                if ($lastError) { Write-Host "Last health-check error: $lastError" }
                if (Test-Path $env:LOG_ERR_FILE) { Get-Content $env:LOG_ERR_FILE -Tail 80 }
                if (Test-Path $env:LOG_OUT_FILE) { Get-Content $env:LOG_OUT_FILE -Tail 80 }
                throw "Application did not become ready within timeout at $healthUrl."
                '''
            }
        }
    }
}
