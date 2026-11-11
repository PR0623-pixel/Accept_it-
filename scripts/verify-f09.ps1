
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"

Write-Host "[F09] Checking server health..."

try {
    $health = Invoke-RestMethod `
        -Uri "$baseUrl/actuator/health" `
        -Method Get `
        -TimeoutSec 10

    if ($health.status -ne "UP") {
        throw "Health status is not UP"
    }

    Write-Host "[PASS] Health status: UP"

    $db = Invoke-RestMethod `
        -Uri "$baseUrl/actuator/health/database" `
        -Method Get `
        -TimeoutSec 10

    if ($db.status -ne "UP") {
        throw "Database health is not UP"
    }

    Write-Host "[PASS] Database status: UP"
    Write-Host "[F09] All checks passed."
}
catch {
    Write-Host "[FAIL] F09 health check failed."
    Write-Host $_.Exception.Message
    exit 1
}
