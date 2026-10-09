# Iceberg Keyboard — developer commands. Run `make help`.
SHELL := /bin/bash

ANDROID_HOME ?= $(HOME)/Library/Android/sdk
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 17 2>/dev/null || echo "$$(brew --prefix openjdk@17 2>/dev/null)/libexec/openjdk.jdk/Contents/Home")
export ANDROID_HOME JAVA_HOME

ADB := $(ANDROID_HOME)/platform-tools/adb
GRADLE := ./gradlew --console=plain
VERSION := $(shell sed -n 's/.*versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
DEBUG_IME := com.iceberg.keyboard.debug/com.iceberg.keyboard.ime.IcebergImeService

.PHONY: help setup test lint debug release check install install-release enable emulator keystore clean

help: ## Show this help
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

setup: ## Install JDK 17 + Android SDK (WITH_EMULATOR=1 adds an Android 10 emulator)
	./scripts/setup-mac.sh

test: ## Run JVM unit tests
	$(GRADLE) :app:testDebugUnitTest

lint: ## Run Android lint
	$(GRADLE) :app:lintDebug

debug: ## Build the debug APK
	$(GRADLE) :app:assembleDebug
	@echo "APK: app/build/outputs/apk/debug/app-debug.apk"

check: ## Tests + lint + debug build (what CI runs)
	$(GRADLE) :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

release: ## Build the signed release APK into dist/
	@[ -f keystore.properties ] || echo "WARNING: keystore.properties missing - signing with the DEBUG key (run 'make keystore')."
	$(GRADLE) :app:assembleRelease
	@mkdir -p dist && cp app/build/outputs/apk/release/app-release.apk dist/IcebergKeyboard-$(VERSION).apk
	@echo "APK: dist/IcebergKeyboard-$(VERSION).apk"

install: ## Install debug build on the connected phone/emulator and select the keyboard
	$(GRADLE) :app:installDebug
	$(MAKE) enable

install-release: release ## Install the release APK on the connected phone
	$(ADB) install -r dist/IcebergKeyboard-$(VERSION).apk
	-$(ADB) shell ime enable com.iceberg.keyboard/.ime.IcebergImeService
	$(ADB) shell ime set com.iceberg.keyboard/.ime.IcebergImeService

enable: ## Enable + select the debug keyboard over adb
	$(ADB) shell ime enable $(DEBUG_IME)
	$(ADB) shell ime set $(DEBUG_IME)

emulator: ## Boot the iceberg_s9 emulator in the background
	@nohup $(ANDROID_HOME)/emulator/emulator -avd iceberg_s9 -no-audio -no-boot-anim >/dev/null 2>&1 &
	@echo "Waiting for boot..."; $(ADB) wait-for-device; until [ "$$($(ADB) shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done; echo "Emulator ready."

keystore: ## Create a release signing key (keystore/release.jks + keystore.properties)
	./scripts/create-keystore.sh

clean: ## Remove build outputs
	$(GRADLE) clean
	rm -rf dist
