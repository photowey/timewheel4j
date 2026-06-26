SHELL := /bin/bash

MVN ?= mvn
JAVA ?= java
GITNEXUS ?= npx gitnexus

BENCHMARK_JAR := timewheel4j-core/target/timewheel4j-core-benchmarks.jar
BENCHMARK_RESULT_DIR := timewheel4j-core/target

.PHONY: help
help:
	@printf '%s\n' 'timewheel4j make targets:'
	@printf '  %-24s %s\n' 'test' 'Run the full Maven test suite'
	@printf '  %-24s %s\n' 'verify' 'Run full reactor verification and JaCoCo checks'
	@printf '  %-24s %s\n' 'toolchain-verify' 'Run verification with the Maven toolchain profile'
	@printf '  %-24s %s\n' 'check' 'Run Checkstyle checks'
	@printf '  %-24s %s\n' 'spring-test' 'Run Spring Boot 2 starter smoke test'
	@printf '  %-24s %s\n' 'boot3-test' 'Run Spring Boot 3 starter smoke test'
	@printf '  %-24s %s\n' 'benchmark-package' 'Build the JMH benchmark jar'
	@printf '  %-24s %s\n' 'benchmark' 'Build and run all benchmarks'
	@printf '  %-24s %s\n' 'benchmark-smoke' 'Run a quick timewheel benchmark smoke test'
	@printf '  %-24s %s\n' 'benchmark-stress' 'Run the stress benchmark with default params'
	@printf '  %-24s %s\n' 'release-deploy' 'Deploy with toolchain and release profiles'
	@printf '  %-24s %s\n' 'diff-check' 'Check git diff whitespace/errors'
	@printf '  %-24s %s\n' 'gitnexus-analyze' 'Refresh GitNexus index'
	@printf '  %-24s %s\n' 'gitnexus-detect' 'Detect GitNexus changes'
	@printf '  %-24s %s\n' 'clean' 'Clean Maven build outputs'

.PHONY: test
test:
	$(MVN) test

.PHONY: verify
verify:
	$(MVN) verify

.PHONY: toolchain-verify
toolchain-verify:
	$(MVN) -Ptoolchain verify

.PHONY: check
check:
	$(MVN) checkstyle:check -T 1C

.PHONY: spring-test
spring-test:
	$(MVN) -pl timewheel4j-spring/timewheel4j-spring-boot-starter -am \
		-Dtest=Timewheel4jBootStarterTest \
		-Dsurefire.failIfNoSpecifiedTests=false test

.PHONY: boot3-test
boot3-test:
	$(MVN) -pl timewheel4j-spring/timewheel4j-spring-boot3-starter -am \
		-Dtest=Timewheel4jBoot3StarterTest \
		-Dsurefire.failIfNoSpecifiedTests=false test

.PHONY: benchmark-package
benchmark-package:
	$(MVN) -Pbenchmark -pl timewheel4j-core -am -DskipTests package

.PHONY: benchmark
benchmark: benchmark-package
	$(JAVA) -jar $(BENCHMARK_JAR)

.PHONY: benchmark-smoke
benchmark-smoke: benchmark-package
	$(JAVA) -jar $(BENCHMARK_JAR) '.*scheduleOnTimewheel.*' \
		-p taskCount=1000 \
		-p maxDelayMillis=100 \
		-p cancelPercent=0 \
		-wi 1 -i 1 -r 200ms -w 200ms -f 1 \
		-rf json -rff $(BENCHMARK_RESULT_DIR)/jmh-smoke.json

.PHONY: benchmark-stress
benchmark-stress: benchmark-package
	$(JAVA) -jar $(BENCHMARK_JAR) '.*TimerStressBenchmark.*'

.PHONY: release-deploy
release-deploy:
	$(MVN) -Ptoolchain,release -DskipTests deploy

.PHONY: diff-check
diff-check:
	git diff --check

.PHONY: gitnexus-analyze
gitnexus-analyze:
	$(GITNEXUS) analyze

.PHONY: gitnexus-detect
gitnexus-detect:
	$(GITNEXUS) detect-changes

.PHONY: clean
clean:
	$(MVN) clean
