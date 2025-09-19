#!/bin/bash

# Android AutoClicker Test Execution Script
# This script runs various test suites for the AutoClicker application

set -e  # Exit on any error

echo "=========================================="
echo "Android AutoClicker Test Execution Script"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to run tests with error handling
run_test() {
    local test_name="$1"
    local test_command="$2"
    
    print_status "Running $test_name..."
    
    if eval "$test_command"; then
        print_success "$test_name completed successfully"
        return 0
    else
        print_error "$test_name failed"
        return 1
    fi
}

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    print_error "gradlew not found. Please run this script from the project root directory."
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

# Parse command line arguments
UNIT_TESTS=true
INTEGRATION_TESTS=true
DEVICE_TESTS=false
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --unit-only)
            UNIT_TESTS=true
            INTEGRATION_TESTS=false
            DEVICE_TESTS=false
            shift
            ;;
        --integration-only)
            UNIT_TESTS=false
            INTEGRATION_TESTS=true
            DEVICE_TESTS=false
            shift
            ;;
        --device-only)
            UNIT_TESTS=false
            INTEGRATION_TESTS=false
            DEVICE_TESTS=true
            shift
            ;;
        --all)
            UNIT_TESTS=true
            INTEGRATION_TESTS=true
            DEVICE_TESTS=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --unit-only        Run only unit tests"
            echo "  --integration-only Run only integration tests"
            echo "  --device-only      Run only device tests (requires connected device)"
            echo "  --all              Run all tests including device tests"
            echo "  --verbose          Enable verbose output"
            echo "  --help             Show this help message"
            echo ""
            echo "Default: Run unit and integration tests (no device tests)"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set verbose flag for gradle
GRADLE_VERBOSE=""
if [ "$VERBOSE" = true ]; then
    GRADLE_VERBOSE="--info"
fi

# Test results tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo ""
print_status "Test Configuration:"
echo "  Unit Tests: $UNIT_TESTS"
echo "  Integration Tests: $INTEGRATION_TESTS"
echo "  Device Tests: $DEVICE_TESTS"
echo "  Verbose Output: $VERBOSE"
echo ""

# Clean build before running tests
print_status "Cleaning project..."
./gradlew clean $GRADLE_VERBOSE

# Run Unit Tests
if [ "$UNIT_TESTS" = true ]; then
    echo ""
    echo "=========================================="
    echo "UNIT TESTS"
    echo "=========================================="
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    if run_test "Unit Tests" "./gradlew test $GRADLE_VERBOSE"; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
fi

# Run Integration Tests
if [ "$INTEGRATION_TESTS" = true ]; then
    echo ""
    echo "=========================================="
    echo "INTEGRATION TESTS"
    echo "=========================================="
    
    # Check if device/emulator is connected for integration tests
    if command -v adb &> /dev/null; then
        DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
        if [ "$DEVICES" -eq 0 ]; then
            print_warning "No connected devices found. Integration tests may fail."
            print_warning "Please connect a device or start an emulator."
        else
            print_status "Found $DEVICES connected device(s)"
        fi
    fi
    
    # Run individual integration test suites
    integration_tests=(
        "UI-ViewModel Integration Tests:com.autoclicker.android.integration.ConfigFragmentViewModelIntegrationTest"
        "Service Integration Tests:com.autoclicker.android.integration.AutoClickServiceIntegrationTest"
        "Full Cycle Integration Tests:com.autoclicker.android.integration.FullAutoClickCycleIntegrationTest"
        "Repository Integration Tests:com.autoclicker.android.integration.RepositoryImageMatcherIntegrationTest"
        "Component Interaction Tests:com.autoclicker.android.integration.ComponentInteractionTest"
    )
    
    for test_info in "${integration_tests[@]}"; do
        IFS=':' read -r test_name test_class <<< "$test_info"
        TOTAL_TESTS=$((TOTAL_TESTS + 1))
        if run_test "$test_name" "./gradlew connectedAndroidTest --tests \"$test_class\" $GRADLE_VERBOSE"; then
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    done
fi

# Run Device Tests
if [ "$DEVICE_TESTS" = true ]; then
    echo ""
    echo "=========================================="
    echo "DEVICE TESTS"
    echo "=========================================="
    
    # Check for connected devices
    if command -v adb &> /dev/null; then
        DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
        if [ "$DEVICES" -eq 0 ]; then
            print_error "No connected devices found. Device tests require a connected device or emulator."
            print_error "Please connect a device or start an emulator and try again."
            exit 1
        else
            print_status "Found $DEVICES connected device(s)"
        fi
    else
        print_error "ADB not found. Please ensure Android SDK is properly installed."
        exit 1
    fi
    
    # Warn about accessibility service requirement
    print_warning "Device tests require the AutoClicker accessibility service to be enabled."
    print_warning "Please ensure the service is enabled in device settings for full test coverage."
    echo ""
    
    # Run individual device test suites
    device_tests=(
        "Accessibility Service Device Tests:com.autoclicker.android.device.AccessibilityServiceDeviceTest"
        "Image Recognition Device Tests:com.autoclicker.android.device.ImageRecognitionDeviceTest"
        "Performance Stability Device Tests:com.autoclicker.android.device.PerformanceStabilityDeviceTest"
    )
    
    for test_info in "${device_tests[@]}"; do
        IFS=':' read -r test_name test_class <<< "$test_info"
        TOTAL_TESTS=$((TOTAL_TESTS + 1))
        if run_test "$test_name" "./gradlew connectedAndroidTest --tests \"$test_class\" $GRADLE_VERBOSE"; then
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    done
fi

# Test Summary
echo ""
echo "=========================================="
echo "TEST EXECUTION SUMMARY"
echo "=========================================="

echo "Total Test Suites: $TOTAL_TESTS"
echo "Passed: $PASSED_TESTS"
echo "Failed: $FAILED_TESTS"

if [ $FAILED_TESTS -eq 0 ]; then
    print_success "All tests passed! ✅"
    echo ""
    echo "The AutoClicker application has successfully passed all executed tests."
    echo "The app is ready for deployment or further development."
else
    print_error "Some tests failed! ❌"
    echo ""
    echo "Please review the test output above to identify and fix the failing tests."
    echo "Check the following common issues:"
    echo "  - Accessibility service not enabled (for device tests)"
    echo "  - Device compatibility issues"
    echo "  - Network connectivity problems"
    echo "  - Insufficient device resources"
fi

echo ""
echo "Test reports can be found in:"
echo "  - Unit tests: app/build/reports/tests/testDebugUnitTest/"
echo "  - Integration/Device tests: app/build/reports/androidTests/connected/"

# Exit with appropriate code
if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi