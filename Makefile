JUNIT_URL := https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar
JUNIT_JAR := libs/junit.jar

SRCS := $(wildcard src/*.java)
TESTS := $(wildcard test/*.java)

SPOTBUGS_VER := 4.9.3
SPOTBUGS_URL := https://repo1.maven.org/maven2/com/github/spotbugs/spotbugs/$(SPOTBUGS_VER)/spotbugs-$(SPOTBUGS_VER).zip
SPOTBUGS_JAR := libs/spotbugs-$(SPOTBUGS_VER)/lib/spotbugs.jar
comma := ,
space := $(empty) $(empty)
MAIN_CLASSES := $(subst $(space),$(comma),$(notdir $(basename $(SRCS))))

.PHONY: deps build test spotbugs clean

deps: $(JUNIT_JAR)

$(JUNIT_JAR):
	@if not exist libs mkdir libs
	@curl -sSL -o $@ $(JUNIT_URL)

build: deps
	@if not exist build mkdir build
	javac --release 17 -d build -cp $(JUNIT_JAR) $(SRCS) $(TESTS)

ifdef JAVA_HOME
JAVA := $(subst \,/,$(JAVA_HOME))/bin/java.exe
else
JAVA := java
endif

test: build
	"$(JAVA)" -jar $(JUNIT_JAR) --class-path build --scan-class-path

$(SPOTBUGS_JAR):
	@if not exist libs mkdir libs
	@curl -sSL -o libs/spotbugs.zip $(SPOTBUGS_URL)
	@tar -xf libs/spotbugs.zip -C libs

# Analyse only src/ classes; JUnit is on the aux classpath so test
# classes resolve. -exitcode makes any finding fail the build.
spotbugs: build $(SPOTBUGS_JAR)
	"$(JAVA)" -jar $(SPOTBUGS_JAR) -textui -effort:max -low -exitcode \
		-auxclasspath $(JUNIT_JAR) -onlyAnalyze $(MAIN_CLASSES) build

clean:
	rm -rf build libs
