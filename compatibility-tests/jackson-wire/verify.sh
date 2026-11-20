#!/usr/bin/env bash
set -euo pipefail

module="$(cd "$(dirname "$0")" && pwd)"
: "${JDK8_HOME:?Set JDK8_HOME}" "${JDK17_HOME:?Set JDK17_HOME}" "${JDK25_HOME:?Set JDK25_HOME}"
export JAVA_HOME="$JDK17_HOME" PATH="$JDK17_HOME/bin:$PATH"
mkdir -p "$module/target/wire"
reports="$(mktemp -d "$module/target/wire/matrix.XXXXXXXX")"
printf 'Jackson wire results: %s\n' "$reports"

run() {
    local job="$1" pom="$2" boot="$3" jdk="$4" java="$5" codec="$6" action="$7" fixture="$8"
    shift 8
    if [[ "$boot" == 3.5.15 ]]; then
        set -- -Pboot3-jackson3-annotations "$@"
    fi
    # The report directory lives under target/wire. Preserve it across Maven runs.
    if "${WIRE_MAVEN:-mvn}" "$@" -f "$module/$pom" \
        "-Dcompat.test.jvm=$java" "-Dboot.version=$boot" "-Dwire.boot=$boot" "-Dwire.jdk=$jdk" \
        "-Dwire.codec=$codec" "-Dwire.action=$action" "-Dwire.fixtures=$reports/$fixture" \
        -Dtest=JacksonWireTest test > "$reports/$job.log" 2>&1; then
        cp "$module/target/surefire-reports/TEST-cn.iocoder.langchain4j.compatibility.JacksonWireTest.xml" \
            "$reports/$job.xml"
        printf 'Passed %s\n' "$job"
    else
        printf 'Failed %s. See %s\n' "$job" "$reports/$job.log" >&2
        return 1
    fi
}

boot2() {
    local job="$1" action="$2" fixture="$3"
    shift 3
    run "$job" pom-boot2.xml 2.7.18 1.8 "$JDK8_HOME/bin/java" Jackson "$action" "$fixture" "$@"
}

boot2 write-boot2 write boot2 "$@"
for version in 3.5.15 4.1.2; do
    for jdk in 17 25; do
        jdk_home="$JDK17_HOME"
        if [[ "$jdk" == 25 ]]; then
            jdk_home="$JDK25_HOME"
        fi
        fixture="boot-$version-jdk$jdk"
        run "write-$fixture" pom.xml "$version" "$jdk" "$jdk_home/bin/java" Jackson3 write "$fixture" "$@"
        run "$fixture-reads-boot2" pom.xml "$version" "$jdk" "$jdk_home/bin/java" Jackson3 read boot2 "$@"
        boot2 "boot2-reads-$fixture" read "$fixture" "$@"
    done
done
