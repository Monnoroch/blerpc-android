#!/bin/sh

# Using 'which' command find full path for '$0', that must be a program for execute.
JAR_PATH=`which "$0" 2>/dev/null`
# If 'which' doesn't find full path, then use program file in current folder if it exists.
[ $? -gt  0 -a -f "$0" ] && JAR_PATH="./$0"
JAVA_OPT=""
PROG_OPT=""

# Parse options to determine which ones are for Java and which ones are for the Program
while [ $# -gt 0 ] ; do
    case $1 in
        -Xm*) JAVA_OPT="$JAVA_OPT $1" ;;
        -D*)  JAVA_OPT="$JAVA_OPT $1" ;;
        *)    PROG_OPT="$PROG_OPT $1" ;;
    esac
    shift
done

exec java $JAVA_OPT -jar $JAR_PATH $PROG_OPT
