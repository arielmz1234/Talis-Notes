#!/bin/sh
# Prints the UDID of the newest available simulator whose name starts with $1 ("iPad Pro 13", "iPhone 16"…),
# falling back to any iPad / iPhone.
want="$1"
kind="$2"   # iPad or iPhone
json=$(xcrun simctl list devices available -j)
pick() {
  echo "$json" | jq -r --arg p "$1" '[.devices | to_entries[] | select(.key | test("iOS")) | .value[] | select(.name | startswith($p))] | last | .udid // empty'
}
# An exact name first ("iPhone 16" isn't "iPhone 16e"), then any that starts with it.
id=$(echo "$json" | jq -r --arg p "$1" '[.devices | to_entries[] | select(.key | test("iOS")) | .value[] | select(.name == $p)] | last | .udid // empty')
[ -z "$id" ] && id=$(pick "$want")
[ -z "$id" ] && id=$(pick "$kind")
echo "$id"
