# ============================================================
# Secufusion - Start All Backend Services
# ============================================================
# 
# USAGE: Open 4 separate PowerShell terminals and run each
#        section in order (wait for each one to fully start
#        before starting the next).
#
# Your Gateway is already running on port 8083.
# This script uses the remote properties from secufusion 6.properties
# ============================================================

$PROPS_FILE = "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\secufusion 6.properties"
$BASE_DIR   = "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Secufusion Service Starter" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Your Gateway is already running on port 8083." -ForegroundColor Green
Write-Host ""
Write-Host "Copy and paste the commands below into SEPARATE" -ForegroundColor Yellow
Write-Host "PowerShell terminals (one per service)." -ForegroundColor Yellow
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TERMINAL 1: Start Eureka (port 8761)" -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan
Write-Host @"

cd "$BASE_DIR\sfn-eureka-api 3\sfn-eureka-api"
.\mvnw.cmd spring-boot:run

"@ -ForegroundColor Green
Write-Host "Wait until you see 'Started SfnEurekaApiApplication' in the console." -ForegroundColor Yellow
Write-Host "Verify at: http://localhost:8761" -ForegroundColor Yellow
Write-Host ""

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TERMINAL 2: Start IAM Service (port 8084)" -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan
Write-Host @"

`$env:file_path="$PROPS_FILE"
cd "$BASE_DIR\sfn-iam-api 4\sfn-iam-api"
.\mvnw.cmd spring-boot:run

"@ -ForegroundColor Green
Write-Host "Wait until you see 'Started' message before starting next service." -ForegroundColor Yellow
Write-Host ""

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TERMINAL 3: Start Tenant Service (port 8085)" -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan
Write-Host @"

`$env:file_path="$PROPS_FILE"
cd "$BASE_DIR\sfn-tenants-api 3\sfn-tenants-api"
.\mvnw.cmd spring-boot:run

"@ -ForegroundColor Green
Write-Host ""

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TERMINAL 4: Start Events Service (port 8086)" -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan
Write-Host @"

`$env:file_path="$PROPS_FILE"
cd "$BASE_DIR\sfn-events-api 2\sfn-events-api"
.\mvnw.cmd spring-boot:run

"@ -ForegroundColor Green
Write-Host ""

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  VERIFICATION" -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "After all services are running, verify:" -ForegroundColor White
Write-Host "  Eureka:  http://localhost:8761" -ForegroundColor White
Write-Host "  Gateway: http://localhost:8083/swagger-ui.html" -ForegroundColor White
Write-Host "  IAM:     http://localhost:8084/api/iam/regions" -ForegroundColor White
Write-Host ""
Write-Host "All services should appear registered in Eureka dashboard." -ForegroundColor Yellow
