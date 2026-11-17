$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"

Write-Host "[Swagger] Checking OpenAPI document..."
try {
    $api = Invoke-RestMethod -Uri "$baseUrl/v3/api-docs" -Method Get -TimeoutSec 15
    if (-not $api.openapi -or -not $api.openapi.StartsWith("3.")) {
        throw "Invalid OpenAPI version"
    }
    if ($api.info.title -ne "AcceptIt Backend REST API") {
        throw "Unexpected API title"
    }

    $required = @(
        @{ path = "/api/rooms"; method = "post" },
        @{ path = "/api/rooms/{roomCode}/join"; method = "post" },
        @{ path = "/api/rooms/ready"; method = "post" },
        @{ path = "/api/game/{roomId}/start"; method = "post" },
        @{ path = "/api/debate/chain"; method = "post" },
        @{ path = "/api/debate/turn"; method = "post" },
        @{ path = "/api/debate/{roomId}/state"; method = "get" },
        @{ path = "/api/debate/{roomId}/finish"; method = "post" }
    )
    foreach ($route in $required) {
        $entry = $api.paths.PSObject.Properties[$route.path]
        if ($null -eq $entry -or $null -eq $entry.Value.PSObject.Properties[$route.method]) {
            throw "Missing endpoint: $($route.method.ToUpper()) $($route.path)"
        }
    }
    Write-Host "[PASS] OpenAPI 3 and all 8 REST endpoints"

    $ui = Invoke-WebRequest -Uri "$baseUrl/swagger-ui.html" -MaximumRedirection 5 -TimeoutSec 15 -UseBasicParsing
    if ($ui.StatusCode -ne 200) {
        throw "Swagger UI returned HTTP $($ui.StatusCode)"
    }
    Write-Host "[PASS] Swagger UI is reachable"
    Write-Host "[Swagger] All checks passed."
}
catch {
    Write-Host "[FAIL] Swagger check failed."
    Write-Host $_.Exception.Message
    exit 1
}
