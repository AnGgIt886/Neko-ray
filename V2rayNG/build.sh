#!/bin/bash

# =============================================
# ENHANCED GRADLE RUNNER WITH DAEMON MANAGEMENT
# =============================================
# Usage: 
#   ./run_gradle.sh [task] [options]
# 
# Features:
# - Automatic daemon management (stop on success/failure)
# - Enhanced error handling
# - Build notifications
# - System resource monitoring
# - Cleanup of old build files

# Configuration
DEFAULT_TASK="build"
LOG_LEVEL="info"
LOG_FILE=""
COLORS_ENABLED=true
TIMESTAMP_ENABLED=true
DRY_RUN=false
PARALLEL=false
PROFILE=false
SHOW_DEPENDENCIES=false
LIST_TASKS=false
DAEMON_ACTION=""
AUTO_DAEMON=true  # Automatically manage daemon
CLEANUP_OLD_FILES=false  # Cleanup old build files
MAX_DAEMON_USAGE=70  # Max CPU% before warning
NOTIFICATIONS=true  # Show desktop notifications
GRADLE_ARGS=()

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Show help function (moved to the top)
show_help() {
    echo -e "${GREEN}Advanced Gradle Runner Script${NC}"
    echo "Usage: $0 [task] [options]"
    echo
    echo "Common Tasks:"
    echo "  build, clean, test, run, assemble*, bundle*, check, lint*"
    echo
    echo "Options:"
    echo "  --debug, --info, --warn, --quiet  Set log level"
    echo "  --log-file [file]                 Specify log file"
    echo "  --no-color                        Disable colored output"
    echo "  --no-timestamp                    Disable timestamps"
    echo "  --dry-run                         Show what would be executed"
    echo "  --parallel                        Enable parallel execution"
    echo "  --profile                         Generate build profile report"
    echo "  --dependencies                    Show project dependencies"
    echo "  --tasks                           List available tasks"
    echo "  --daemon <action>                 Control Gradle daemon (start|stop|status|restart)"
    echo "  --no-auto-daemon                  Disable automatic daemon management"
    echo "  --cleanup                         Cleanup old build files before running"
    echo "  --help                            Show this help message"
    echo
    echo "Features:"
    echo "  - Automatic daemon management"
    echo "  - Build notifications"
    echo "  - System resource monitoring"
    echo "  - Cleanup of old build files"
    echo
    echo "Examples:"
    echo "  $0 build --parallel --profile"
    echo "  $0 --dependencies --debug"
    echo "  $0 --daemon status"
    echo "  $0 test --no-auto-daemon"
}

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case "$1" in
        build|clean|test|run|assemble*|bundle*|check|lint*)
            DEFAULT_TASK="$1"
            shift
            ;;
        --debug)
            LOG_LEVEL="debug"
            shift
            ;;
        --info)
            LOG_LEVEL="info"
            shift
            ;;
        --warn)
            LOG_LEVEL="warn"
            shift
            ;;
        --quiet)
            LOG_LEVEL="quiet"
            shift
            ;;
        --log-file)
            if [ -n "$2" ] && [ "${2:0:1}" != "-" ]; then
                LOG_FILE="$2"
                shift 2
            else
                LOG_FILE="gradle_$(date +%Y%m%d_%H%M%S).log"
                shift
            fi
            ;;
        --no-color)
            COLORS_ENABLED=false
            shift
            ;;
        --no-timestamp)
            TIMESTAMP_ENABLED=false
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --parallel)
            PARALLEL=true
            GRADLE_ARGS+=("--parallel")
            shift
            ;;
        --profile)
            PROFILE=true
            GRADLE_ARGS+=("--profile")
            shift
            ;;
        --dependencies)
            SHOW_DEPENDENCIES=true
            DEFAULT_TASK="dependencies"
            shift
            ;;
        --tasks)
            LIST_TASKS=true
            DEFAULT_TASK="tasks"
            shift
            ;;
        --daemon)
            if [ -n "$2" ] && [ "${2:0:1}" != "-" ]; then
                DAEMON_ACTION="$2"
                AUTO_DAEMON=false  # Disable auto management if manually controlling
                shift 2
            else
                echo -e "${RED}Error: --daemon requires an action (start|stop|status|restart)${NC}"
                exit 1
            fi
            ;;
        --no-auto-daemon)
            AUTO_DAEMON=false
            shift
            ;;
        --cleanup)
            CLEANUP_OLD_FILES=true
            shift
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            GRADLE_ARGS+=("$1")
            shift
            ;;
    esac
done

# Show desktop notification
show_notification() {
    if [ "$NOTIFICATIONS" = true ] && command -v notify-send >/dev/null 2>&1; then
        local title="Gradle Build"
        local message="$1"
        local urgency="$2"
        
        notify-send "$title" "$message" -u "$urgency"
    fi
}

# Check system resources
check_resources() {
    local cpu_usage=$(top -bn1 | grep "Cpu(s)" | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | awk '{print 100 - $1}')
    local mem_usage=$(free -m | awk '/Mem:/ {printf "%.1f", $3/$2*100}')
    
    if (( $(echo "$cpu_usage > $MAX_DAEMON_USAGE" | bc -l) )); then
        echo -e "${YELLOW}Warning: High CPU usage detected (${cpu_usage}%)${NC}"
    fi
    
    echo -e "${CYAN}System Resources - CPU: ${cpu_usage}%, Memory: ${mem_usage}%${NC}"
}

# Cleanup old build files
cleanup_old_files() {
    echo -e "${BLUE}Cleaning up old build files...${NC}"
    find . -name "build" -type d -exec rm -rf {} + 2>/dev/null || true
    find . -name ".gradle" -type d -exec rm -rf {} + 2>/dev/null || true
    find . -name "*.log" -type f -mtime +7 -exec rm -f {} + 2>/dev/null || true
}

# Handle daemon actions
handle_daemon() {
    case "$DAEMON_ACTION" in
        start)
            echo -e "${BLUE}Starting Gradle daemon...${NC}"
            ./gradlew --daemon
            show_notification "Gradle daemon started" "normal"
            ;;
        stop)
            echo -e "${BLUE}Stopping Gradle daemon...${NC}"
            ./gradlew --stop
            show_notification "Gradle daemon stopped" "normal"
            ;;
        status)
            echo -e "${BLUE}Gradle daemon status:${NC}"
            ./gradlew --status || true
            ;;
        restart)
            echo -e "${BLUE}Restarting Gradle daemon...${NC}"
            ./gradlew --stop
            ./gradlew --daemon
            show_notification "Gradle daemon restarted" "normal"
            ;;
        *)
            echo -e "${RED}Error: Unknown daemon action '$DAEMON_ACTION'${NC}"
            exit 1
            ;;
    esac
    exit 0
}

# Format log output
format_log() {
    while IFS= read -r line; do
        # Add timestamp
        if $TIMESTAMP_ENABLED; then
            line="[$(date '+%H:%M:%S')] $line"
        fi

        # Add colors if enabled
        if $COLORS_ENABLED; then
            line=$(echo "$line" | sed \
                -e "s/\(BUILD SUCCESSFUL\)/${GREEN}\1${NC}/g" \
                -e "s/\(BUILD FAILED\)/${RED}\1${NC}/g" \
                -e "s/\(WARN[^ ]*\)/${YELLOW}\1${NC}/g" \
                -e "s/\(ERROR[^ ]*\|FAILURE\)/${RED}\1${NC}/g" \
                -e "s/\(DEBUG[^ ]*\)/${CYAN}\1${NC}/g" \
                -e "s/\(SKIPPED\)/${BLUE}\1${NC}/g")
        fi

        echo "$line"
    done
}

# Filter log by level
filter_log() {
    case "$LOG_LEVEL" in
        debug) cat ;;
        info)  grep -E -v "DEBUG|TRACE" ;;
        warn)  grep -E -v "DEBUG|TRACE|INFO" ;;
        quiet) grep -E -a "BUILD SUCCESSFUL|BUILD FAILED|ERROR|WARN|FAILURE" ;;
    esac
}

# Show execution summary
show_summary() {
    local duration=$1
    echo -e "\n${BLUE}=== Build Summary ===${NC}"
    echo -e "Task:        ${DEFAULT_TASK}"
    echo -e "Log level:   ${LOG_LEVEL}"
    [ -n "$LOG_FILE" ] && echo -e "Log file:    ${LOG_FILE}"
    echo -e "Duration:    ${duration} seconds"
    
    if [ $EXIT_CODE -eq 0 ]; then
        echo -e "Result:      ${GREEN}SUCCESS${NC}"
        show_notification "Build succeeded in ${duration} seconds" "normal"
    else
        echo -e "Result:      ${RED}FAILED${NC} (code $EXIT_CODE)"
        show_notification "Build failed after ${duration} seconds" "critical"
    fi
}

# Handle daemon action if specified
[ -n "$DAEMON_ACTION" ] && handle_daemon

# Show help if no arguments
[ $# -eq 0 ] && [ "$DEFAULT_TASK" = "build" ] && show_help

# Check system resources before build
check_resources

# Cleanup old files if requested
$CLEANUP_OLD_FILES && cleanup_old_files

# Execute Gradle
echo -e "${BLUE}🚀 Running: ./gradlew ${DEFAULT_TASK} ${GRADLE_ARGS[*]}${NC}"
echo -e "${CYAN}🔧 Log level: ${LOG_LEVEL}${NC}"
[ -n "$LOG_FILE" ] && echo -e "${CYAN}📝 Log file: ${LOG_FILE}${NC}"
$PARALLEL && echo -e "${CYAN}⚡ Parallel execution enabled${NC}"
$PROFILE && echo -e "${CYAN}📊 Profile report will be generated${NC}"
$AUTO_DAEMON && echo -e "${CYAN}🤖 Automatic daemon management enabled${NC}"

if $DRY_RUN; then
    echo -e "${YELLOW}Dry run mode - would execute:${NC}"
    echo "bash gradlew ${DEFAULT_TASK} ${GRADLE_ARGS[*]} --console=plain"
    exit 0
fi

START_TIME=$(date +%s)

# Function to stop daemon if needed
stop_daemon_if_needed() {
    if [ "$AUTO_DAEMON" = true ]; then
        echo -e "${BLUE}Stopping Gradle daemon after build...${NC}"
        ./gradlew --stop >/dev/null 2>&1 || true
    fi
}

# Trap signals to ensure daemon is stopped
trap 'stop_daemon_if_needed' EXIT

if [ -z "$LOG_FILE" ]; then
    bash gradlew "${DEFAULT_TASK}" "${GRADLE_ARGS[@]}" --console=plain 2>&1 | filter_log | format_log
else
    echo -e "${CYAN}Writing logs to ${LOG_FILE}${NC}"
    bash gradlew "${DEFAULT_TASK}" "${GRADLE_ARGS[@]}" --console=plain 2>&1 | tee "$LOG_FILE" | filter_log | format_log
fi

EXIT_CODE=${PIPESTATUS[0]}
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

show_summary $DURATION

# Additional cleanup if build failed
if [ $EXIT_CODE -ne 0 ] && [ "$CLEANUP_OLD_FILES" = false ]; then
    echo -e "${YELLOW}Build failed. Consider running with --cleanup to remove old build files.${NC}"
fi

exit $EXIT_CODE
