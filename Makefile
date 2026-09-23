JUNIT_URL := https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar
JUNIT_JAR := libs/junit.jar

SRCS := $(wildcard src/*.java)
TESTS := $(wildcard test/*.java)

.PHONY: deps build test clean

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

clean:
	rm -rf build libs
