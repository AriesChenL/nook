@echo off
chcp 65001 >nul
cd /d "%~dp0"

if "%~1"=="" goto menu
if /i "%~1"=="up" goto start
if /i "%~1"=="down" goto stop
if /i "%~1"=="status" goto status
if /i "%~1"=="reset" goto reset
goto menu

:menu
echo.
echo  ╔══════════════════════════════════╗
echo  ║       Nook Infrastructure        ║
echo  ╠══════════════════════════════════╣
echo  ║  1. Start   (docker up + SQL)    ║
echo  ║  2. Stop    (docker down)        ║
echo  ║  3. Status  (check services)     ║
echo  ║  4. Reset   (down -v, wipe data) ║
echo  ║  0. Exit                         ║
echo  ╚══════════════════════════════════╝
echo.
set /p choice=Select:
if "%choice%"=="1" goto start
if "%choice%"=="2" goto stop
if "%choice%"=="3" goto status
if "%choice%"=="4" goto reset
if "%choice%"=="0" exit /b 0
goto menu

:start
echo.
echo [1/4] Starting docker containers...
docker compose up -d
if %errorlevel% neq 0 (
    echo [ERROR] docker compose up failed!
    goto end
)

echo.
echo [2/4] Waiting for PostgreSQL...
:pg_wait
docker exec nook-postgres pg_isready -U nook -d nook >nul 2>&1
if %errorlevel% neq 0 (
    timeout /t 2 /nobreak >nul
    goto pg_wait
)
echo        PostgreSQL ready.

echo.
echo [3/4] Running SQL init (idempotent)...
docker exec -i nook-postgres psql -U nook -d nook < sql\01_init_auth.sql 2>nul
docker exec -i nook-postgres psql -U nook -d nook < sql\02_init_user.sql 2>nul
docker exec -i nook-postgres psql -U nook -d nook < sql\03_init_im.sql 2>nul
echo        Done.

echo.
echo [4/4] Services ready:
call :print_status
echo.
echo  Now start Java services in IDEA:
echo    nook-auth :8081 ^> nook-gateway :8080 ^> nook-user :8082 ^> nook-im :8083
goto end

:stop
echo.
echo Stopping all containers...
docker compose down
echo Done.
goto end

:reset
echo.
echo WARNING: This will delete ALL data (PG, Redis, Nacos)!
set /p confirm=Type YES to confirm:
if /i not "%confirm%"=="YES" (
    echo Cancelled.
    goto end
)
echo Resetting...
docker compose down -v
echo All containers and volumes removed.
goto end

:status
echo.
call :print_status
goto end

:print_status
echo  Service            Port    Status
echo  ────────────────   ─────   ──────
for %%s in (nook-postgres:5432,nook-redis:6379,nook-nacos:8848,nook-rmq-namesrv:9876,nook-rmq-broker:10911) do (
    for /f "tokens=1,2 delims=:" %%a in ("%%s") do (
        for /f "tokens=*" %%r in ('docker inspect -f "{{.State.Status}}" %%a 2^>nul ^|^| echo not-found') do (
            set "name=%%a                    "
            set "port=%%b      "
            call echo  %%name:~0,19%% %%port:~0,6%% %%r
        )
    )
)
exit /b

:end
if "%~1"=="" pause
