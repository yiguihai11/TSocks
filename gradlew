#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# @author: Andres Almiray
#

# -----------------------------------------------------------------------------
#
#   Gradle start up script for UN*X
#
# -----------------------------------------------------------------------------

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass any JVM options to this script.
DEFAULT_JVM_OPTS=''

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

# For Darwin, add options to specify how the application appears in the dock
if [ `uname -s` = "Darwin" ]; then
    GRADLE_OPTS="-Xdock:name=$APP_NAME -Xdock:icon=\`dirname \"$0\"\\]/media/gradle.icns\" $GRADLE_OPTS"
fi

# OS specific support (must be 'true' or 'false').
cygwin=false
darwin=false
linux=false
case "`uname`" in
    CYGWIN*) 
        cygwin=true
        ;;
    Darwin*) 
        darwin=true
        ;;
    Linux) 
        linux=true
        ;;
esac

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG="`dirname \"$PRG\"`/$link"
    fi
done
APP_HOME=`dirname "$PRG"`

# Absolutize APP_HOME
APP_HOME=`cd "$APP_HOME" > /dev/null && pwd`

# Add auto-detected JAVA_HOME if it exists
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    # "/path/to/java" "-version"
    # "/path/to/java" -version
    # /path/to/java "-version"
    # /path/to/java -version
    JAVA_EXE="$JAVA_HOME/bin/java"
fi

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
fi

# Increase the maximum number of open file descriptors to prevent 
# "Too many open files" errors during compilation.
if [ "$cygwin" = "false" ] && [ "$darwin" = "false" ] ; then
    # Increase the maximum number of open file descriptors
    if [ "$MAX_FD" = "maximum" ] || [ "$MAX_FD" = "max" ] ; then
        # Use the maximum available, or set MAX_FD != -1 to use that value.
        MAX_FD_LIMIT=`ulimit -H -n`
        if [ $? -eq 0 ] ; then
            if [ "$MAX_FD_LIMIT" != 'unlimited' ] ; then
                ulimit -n $MAX_FD_LIMIT
            fi
        fi
    else
        ulimit -n $MAX_FD
    fi
fi

# Collect all arguments for the java command, following the shell quoting and substitution rules
eval set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"

exec "$JAVACMD" "$@"
