#!/bin/sh
# The MCP client stays on Java8; the upstream Quarkus test server needs a modern JDK.
set -eu
: "${COMPAT_MCP_SERVER_JAVA_HOME:?Set COMPAT_MCP_SERVER_JAVA_HOME to the test server JDK17 or newer}"
export JAVA_HOME="$COMPAT_MCP_SERVER_JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
exec "${COMPAT_JBANG_COMMAND:-jbang}" "$@"
