#! /bin/sh

# 'strict' mode
set -euo pipefail

SOURCE_BRANCH=aosp/metalava-main

echo -n "Checking status"
STATUS=$(git status -s | grep -v "??" || true)
if [[ $STATUS ]]; then
  echo " - ERROR"
  echo
  echo "The following changed files would interfere with merge:"
  echo "$STATUS"
  exit 1
else
  echo " - OK"
fi

echo -n "Checking current branch"
BRANCH=$(git branch --show-current)
if [[ $BRANCH ]]; then
  echo " - OK ($BRANCH)"
else
  echo " - ERROR"
  echo
  echo "No branch found, please run 'repo start <branch>'."
  exit 1
fi

function output_only_on_failure() {
  set +e
  OUTPUT=$(eval "$@" 2>&1)
  EXIT_CODE=$?
  set -e
  if [[ $EXIT_CODE != 0 ]]; then
    echo " - ERROR"
    echo "Command: $@"
    echo $OUTPUT
    exit 1
  fi
}

# Make sure that ${SOURCE_BRANCH} is up to date.
echo -n "Making sure that ${SOURCE_BRANCH} is up to date"
SPLIT_REPOSITORY_BRANCH=${SOURCE_BRANCH/\// }
output_only_on_failure git fetch ${SPLIT_REPOSITORY_BRANCH}
echo " - OK"

echo -n "Checking to see if there is anything to merge"
MERGE_BASE=$(git merge-base HEAD ${SOURCE_BRANCH})
if [[ ${MERGE_BASE} == $(git rev-parse ${SOURCE_BRANCH}) ]]; then
  echo " - NOTHING TO DO"
  exit 0
else
  echo " - CHANGES FOUND"
fi

echo -n "Extracting bugs from merged in changes"
BUGS=$(git log ${MERGE_BASE}..${SOURCE_BRANCH} | (grep -E "^ *Bug: *[0-9]+" || true) | sed "s/Bug://" | sort -u)
echo " - DONE"

echo -n "Performing the merge"
MESSAGE_FILE=$(mktemp)
trap "rm -f ${MESSAGE_FILE}" EXIT

cat > ${MESSAGE_FILE} <<EOF
Merge remote-tracking branch 'aosp/metalava-main' into aosp/main

Merge performed by:
  $0

This merge includes a number of changes so this contains a list of all
the affected bugs.

$(for BUG in $BUGS; do echo "Bug: $BUG"; done)
Test: m checkapi
EOF

output_only_on_failure git merge ${SOURCE_BRANCH} --no-ff -F ${MESSAGE_FILE}

echo " - DONE"
echo "The merge commit has been created. Please do the following before uploading:"
echo "1. Verify the commit by running 'm checkapi'"
echo "2. Review the commit to make sure it includes what is expected"

