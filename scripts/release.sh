#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_IGNORE_REGEX='.*[-_\.](alpha|Alpha|ALPHA|b|beta|Beta|BETA|rc|RC|M|EA)[-_\.]?[0-9]*'

MAVEN_SAMPLE_POM="samples/spring-boot-3.5-maven-sample/pom.xml"
GRADLE_PLUGIN_PROJECT="spring-boot-properties-migrator-gradle-plugin"
GRADLE_SAMPLE_PROJECT="samples/spring-boot-3.5-gradle-sample"
MAVEN_CONFIG_FILE=".mvn/maven.config"
ROOT_POM_FILE="pom.xml"
JRELEASER_FILE="jreleaser.yml"
PRERELEASE_REGEX='(alpha|Alpha|ALPHA|beta|Beta|BETA|rc|RC|M|EA|CR|preview|PREVIEW)'

log() {
  echo "[release-script] $*"
}

die() {
  echo "[release-script] ERROR: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  scripts/release.sh update-deps [--commit]
  scripts/release.sh prepare-release <version>
  scripts/release.sh release <version> [--push]
  scripts/release.sh release-cycle <version> [--push]
  scripts/release.sh prepare-next <next-snapshot-version>

Examples:
  scripts/release.sh update-deps
  scripts/release.sh update-deps --commit
  scripts/release.sh prepare-release 0.1.0
  scripts/release.sh release 0.1.0 --push
  scripts/release.sh release-cycle 0.1.0 --push
  scripts/release.sh prepare-next 0.2.0-SNAPSHOT

Environment:
  JAVA_HOME (optional): If set, used for Gradle commands.
  GRADLE_VERSION (optional): If set, updates both Gradle wrappers to this version during update-deps.
EOF
}

log_phase() {
  local ecosystem="$1"
  local phase="$2"
  log "${ecosystem}: ${phase}"
}

has_worktree_changes() {
  cd "$ROOT_DIR"
  if ! git diff --quiet || ! git diff --cached --quiet; then
    return 0
  fi
  if [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    return 0
  fi
  return 1
}

require_clean_git() {
  cd "$ROOT_DIR"
  git diff --quiet || die "Working tree has unstaged changes."
  git diff --cached --quiet || die "Working tree has staged changes."
  if [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    die "Working tree has untracked files."
  fi
}

validate_release_version() {
  local version="$1"
  [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "Release version must be SemVer without SNAPSHOT (e.g. 1.2.3)."
}

validate_snapshot_version() {
  local version="$1"
  [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]] || die "Next version must end with -SNAPSHOT (e.g. 1.2.4-SNAPSHOT)."
}

next_snapshot_from_release() {
  local version="$1"
  validate_release_version "$version"

  local major minor patch
  IFS='.' read -r major minor patch <<< "$version"
  echo "${major}.${minor}.$((patch + 1))-SNAPSHOT"
}

base_version() {
  local version="$1"
  echo "${version%-SNAPSHOT}"
}

current_revision() {
  cd "$ROOT_DIR"
  local rev
  rev="$(sed -n 's/^-Drevision=//p' "$MAVEN_CONFIG_FILE" | head -n1)"
  [[ -n "$rev" ]] || die "Could not resolve revision from $MAVEN_CONFIG_FILE"
  echo "$rev"
}

latest_maven_stable_version() {
  local group_id="$1"
  local artifact_id="$2"
  local metadata_url
  local version

  metadata_url="https://repo1.maven.org/maven2/${group_id//./\/}/${artifact_id}/maven-metadata.xml"
  version="$(curl -fsSL "$metadata_url" | sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' | grep -Ev "$PRERELEASE_REGEX" | tail -n1)"

  [[ -n "$version" ]] || die "Could not resolve stable version for ${group_id}:${artifact_id}"
  echo "$version"
}

set_gradle_property_version() {
  local file="$1"
  local key="$2"
  local value="$3"
  [[ -f "$file" ]] || die "Missing $file"

  if grep -q "^${key}=" "$file"; then
    sed -i -E "s#^${key}=.*#${key}=${value}#" "$file"
  else
    echo "${key}=${value}" >> "$file"
  fi
}

run_gradle_dependency_updates() {
  cd "$ROOT_DIR"

  local plugin_props_file="$GRADLE_PLUGIN_PROJECT/gradle.properties"
  local sample_props_file="$GRADLE_SAMPLE_PROJECT/gradle.properties"

  log "Updating Gradle plugin dependency versions"
  set_gradle_property_version "$plugin_props_file" "springBootConfigurationMetadataVersion" "$(latest_maven_stable_version org.springframework.boot spring-boot-configuration-metadata)"
  set_gradle_property_version "$plugin_props_file" "snakeyamlVersion" "$(latest_maven_stable_version org.yaml snakeyaml)"
  set_gradle_property_version "$plugin_props_file" "junitJupiterVersion" "$(latest_maven_stable_version org.junit.jupiter junit-jupiter)"
  set_gradle_property_version "$plugin_props_file" "junitPlatformLauncherVersion" "$(latest_maven_stable_version org.junit.platform junit-platform-launcher)"
  set_gradle_property_version "$plugin_props_file" "assertjVersion" "$(latest_maven_stable_version org.assertj assertj-core)"
  set_gradle_property_version "$plugin_props_file" "springJavaFormatPluginVersion" "$(latest_maven_stable_version io.spring.javaformat spring-javaformat-gradle-plugin)"

  log "Updating Gradle sample dependency versions"
  set_gradle_property_version "$sample_props_file" "junitJupiterVersion" "$(latest_maven_stable_version org.junit.jupiter junit-jupiter)"
  set_gradle_property_version "$sample_props_file" "junitPlatformLauncherVersion" "$(latest_maven_stable_version org.junit.platform junit-platform-launcher)"
  set_gradle_property_version "$sample_props_file" "assertjVersion" "$(latest_maven_stable_version org.assertj assertj-core)"
  set_gradle_property_version "$sample_props_file" "springJavaFormatPluginVersion" "$(latest_maven_stable_version io.spring.javaformat spring-javaformat-gradle-plugin)"
}

set_revision() {
  local version="$1"
  cd "$ROOT_DIR"

  if [[ -f "$MAVEN_CONFIG_FILE" ]]; then
    if grep -q '^-Drevision=' "$MAVEN_CONFIG_FILE"; then
      sed -i -E "s#^-Drevision=.*#-Drevision=${version}#" "$MAVEN_CONFIG_FILE"
    else
      echo "-Drevision=${version}" >> "$MAVEN_CONFIG_FILE"
    fi
  else
    die "Missing $MAVEN_CONFIG_FILE"
  fi

  if [[ -f "$ROOT_POM_FILE" ]]; then
    perl -0pi -e 's#<revision>[^<]+</revision>#<revision>'"$version"'</revision>#' "$ROOT_POM_FILE"
  else
    die "Missing $ROOT_POM_FILE"
  fi
}

set_jreleaser_version() {
  local version="$1"
  local release_version
  release_version="$(base_version "$version")"
  cd "$ROOT_DIR"
  if [[ -f "$JRELEASER_FILE" ]]; then
    perl -0pi -e 's#(project:\n(?:[^\n]*\n)*?\s*version:\s*)[^\n]+#${1}'"$release_version"'#' "$JRELEASER_FILE"
  else
    die "Missing $JRELEASER_FILE"
  fi
}

run_gradle() {
  local project_path="$1"
  shift
  if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$JAVA_HOME" ./"$GRADLE_PLUGIN_PROJECT"/gradlew -p "$project_path" "$@"
  else
    ./"$GRADLE_PLUGIN_PROJECT"/gradlew -p "$project_path" "$@"
  fi
}

run_gradle_with_local_wrapper() {
  local wrapper_path="$1"
  local project_path="$2"
  shift 2
  if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$JAVA_HOME" ./$wrapper_path -p "$project_path" "$@"
  else
    ./$wrapper_path -p "$project_path" "$@"
  fi
}

resolve_rewrite_recipe_coordinates() {
  echo "io.moderne.recipe:rewrite-spring:$(latest_maven_stable_version io.moderne.recipe rewrite-spring),org.openrewrite.recipe:rewrite-static-analysis:$(latest_maven_stable_version org.openrewrite.recipe rewrite-static-analysis),org.openrewrite.recipe:rewrite-logging-frameworks:$(latest_maven_stable_version org.openrewrite.recipe rewrite-logging-frameworks),org.openrewrite.recipe:rewrite-testing-frameworks:$(latest_maven_stable_version org.openrewrite.recipe rewrite-testing-frameworks)"
}

maven_update_phase() {
  cd "$ROOT_DIR"

  log_phase "Maven" "update versions"
  ./mvnw -B -ntp org.codehaus.mojo:versions-maven-plugin:update-parent \
    -DallowSnapshots=false \
    -DgenerateBackupPoms=false \
    -Dmaven.version.ignore="$MAVEN_IGNORE_REGEX"

  ./mvnw -B -ntp org.codehaus.mojo:versions-maven-plugin:update-properties \
    -DallowSnapshots=false \
    -DallowMajorUpdates=true \
    -DallowMinorUpdates=true \
    -DallowIncrementalUpdates=true \
    -Dmaven.version.ignore="$MAVEN_IGNORE_REGEX"

  ./mvnw -B -ntp tidy:pom

  ./mvnw -B -ntp -U com.github.ekryd.sortpom:sortpom-maven-plugin:sort \
    -Dsort.predefinedSortOrder=custom_1

  log "Removing Maven backup POM files"
  find . -type f \( -name 'pom.xml.versionsBackup' -o -name 'pom.xml.bak' -o -name '*.versionsBackup' \) -delete
}

maven_rewrite_phase() {
  cd "$ROOT_DIR"
  log_phase "Maven" "run OpenRewrite"

  ./mvnw -B -ntp -Dmaven.gitcommitid.skip=true -Dcyclonedx.skip=true \
    org.openrewrite.maven:rewrite-maven-plugin:run \
    -Drewrite.activeRecipes=org.openrewrite.staticanalysis.CodeCleanup,org.openrewrite.java.logging.slf4j.Slf4jBestPractices,org.openrewrite.java.testing.junit.JUnit6BestPractices,org.openrewrite.java.testing.mockito.MockitoBestPractices,org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0 \
    -Drewrite.recipeArtifactCoordinates=io.moderne.recipe:rewrite-spring:LATEST,org.openrewrite.recipe:rewrite-static-analysis:LATEST,org.openrewrite.recipe:rewrite-logging-frameworks:LATEST,org.openrewrite.recipe:rewrite-testing-frameworks:LATEST \
    -Drewrite.exclusions="**/PetTypeFormatterTests.java"

  ./mvnw -B -ntp -Dmaven.gitcommitid.skip=true -Dcyclonedx.skip=true \
    org.openrewrite.maven:rewrite-maven-plugin:run \
    -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports,org.openrewrite.java.OrderImports \
    -Drewrite.exportDatatables=true
}

maven_format_phase() {
  cd "$ROOT_DIR"
  log_phase "Maven" "format code"
  ./mvnw -B -ntp -U io.spring.javaformat:spring-javaformat-maven-plugin:apply
}

maven_regression_phase() {
  cd "$ROOT_DIR"
  local revision
  revision="$(current_revision)"

  log_phase "Maven" "run regression tests"
  ./mvnw -B -ntp install -pl spring-boot-properties-migrator-maven-plugin -am

  ./samples/spring-boot-3.5-maven-sample/mvnw -B -ntp -f "$MAVEN_SAMPLE_POM" -Dmigrator.version="$revision" test
}

gradle_update_phase() {
  cd "$ROOT_DIR"
  log_phase "Gradle" "update versions"

  run_gradle_dependency_updates

  if [[ -n "${GRADLE_VERSION:-}" ]]; then
    log "Updating Gradle wrappers to ${GRADLE_VERSION}"
    ./"$GRADLE_PLUGIN_PROJECT"/gradlew -p "$GRADLE_PLUGIN_PROJECT" wrapper --gradle-version "$GRADLE_VERSION"
    ./"$GRADLE_SAMPLE_PROJECT"/gradlew -p "$GRADLE_SAMPLE_PROJECT" wrapper --gradle-version "$GRADLE_VERSION"
  fi
}

gradle_rewrite_phase() {
  cd "$ROOT_DIR"
  log_phase "Gradle" "run OpenRewrite"

  local rewrite_main_recipes
  rewrite_main_recipes="org.openrewrite.staticanalysis.CodeCleanup,org.openrewrite.java.logging.slf4j.Slf4jBestPractices,org.openrewrite.java.testing.junit.JUnit6BestPractices,org.openrewrite.java.testing.mockito.MockitoBestPractices,org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0"

  local rewrite_import_recipes
  rewrite_import_recipes="org.openrewrite.java.RemoveUnusedImports,org.openrewrite.java.OrderImports"

  local rewrite_recipe_coordinates
  rewrite_recipe_coordinates="$(resolve_rewrite_recipe_coordinates)"

  run_gradle "$GRADLE_PLUGIN_PROJECT" rewriteRun --no-daemon --console=plain \
    -Drewrite.activeRecipes="$rewrite_main_recipes" \
    -Drewrite.recipeArtifactCoordinates="$rewrite_recipe_coordinates" \
    -Drewrite.exclusions="**/PetTypeFormatterTests.java"

  run_gradle "$GRADLE_PLUGIN_PROJECT" rewriteRun --no-daemon --console=plain \
    -Drewrite.activeRecipes="$rewrite_import_recipes" \
    -Drewrite.recipeArtifactCoordinates="$rewrite_recipe_coordinates" \
    -Drewrite.exportDatatables=true

  run_gradle_with_local_wrapper "$GRADLE_SAMPLE_PROJECT/gradlew" "$GRADLE_SAMPLE_PROJECT" rewriteRun --no-daemon --console=plain \
    -Drewrite.activeRecipes="$rewrite_main_recipes" \
    -Drewrite.recipeArtifactCoordinates="$rewrite_recipe_coordinates"

  run_gradle_with_local_wrapper "$GRADLE_SAMPLE_PROJECT/gradlew" "$GRADLE_SAMPLE_PROJECT" rewriteRun --no-daemon --console=plain \
    -Drewrite.activeRecipes="$rewrite_import_recipes" \
    -Drewrite.recipeArtifactCoordinates="$rewrite_recipe_coordinates" \
    -Drewrite.exportDatatables=true
}

gradle_format_phase() {
  cd "$ROOT_DIR"
  log_phase "Gradle" "format code"

  run_gradle "$GRADLE_PLUGIN_PROJECT" format --no-daemon --console=plain
  run_gradle_with_local_wrapper "$GRADLE_SAMPLE_PROJECT/gradlew" "$GRADLE_SAMPLE_PROJECT" format --no-daemon --console=plain
}

gradle_regression_phase() {
  cd "$ROOT_DIR"
  log_phase "Gradle" "run regression tests"

  run_gradle "$GRADLE_PLUGIN_PROJECT" test functionalTest --no-daemon --console=plain
  run_gradle_with_local_wrapper "$GRADLE_SAMPLE_PROJECT/gradlew" "$GRADLE_SAMPLE_PROJECT" test --no-daemon --console=plain
}

run_update_format_and_regression_for_all() {
  local phases=(
    maven_update_phase
    gradle_update_phase
    maven_rewrite_phase
    gradle_rewrite_phase
    maven_format_phase
    gradle_format_phase
    maven_regression_phase
    gradle_regression_phase
  )

  local phase
  for phase in "${phases[@]}"; do
    "$phase"
  done
}

run_regression_for_all() {
  maven_regression_phase
  gradle_regression_phase
}

commit_update_deps_changes() {
  commit_changes "chore(deps): update Maven and Gradle dependencies" false
}

commit_changes() {
  local message="$1"
  local require_changes="${2:-true}"
  cd "$ROOT_DIR"

  if ! has_worktree_changes; then
    if [[ "$require_changes" == "true" ]]; then
      die "No changes detected to commit for: ${message}"
    fi
    log "No changes detected. Skipping commit: ${message}"
    return
  fi

  git add -A
  if git diff --cached --quiet; then
    if [[ "$require_changes" == "true" ]]; then
      die "No changes detected to commit for: ${message}"
    fi
    log "No changes detected. Skipping commit: ${message}"
    return
  fi

  git commit -m "$message"
}

prepare_release() {
  local version="$1"
  validate_release_version "$version"
  require_clean_git

  log "Setting release version ${version}"
  set_revision "$version"
  set_jreleaser_version "$version"

  log "Running regression tests"
  run_regression_for_all

  commit_changes "chore(release): v${version}" true
  log "Release commit created."
}

release_version() {
  local version="$1"
  local push_changes="${2:-}"
  local tag="v${version}"

  prepare_release "$version"

  if git rev-parse "$tag" >/dev/null 2>&1; then
    die "Tag ${tag} already exists."
  fi

  git tag -a "$tag" -m "$tag"
  log "Created tag ${tag}."

  if [[ "$push_changes" == "--push" ]]; then
    git push
    git push origin "$tag"
    log "Pushed commit and tag ${tag}."
  else
    log "Tag created locally. Run 'git push && git push origin ${tag}' when ready."
  fi
}

release_cycle() {
  local version="$1"
  local push_changes="${2:-}"
  local next_version
  next_version="$(next_snapshot_from_release "$version")"

  release_version "$version"
  prepare_next_iteration "$next_version"

  if [[ "$push_changes" == "--push" ]]; then
    git push
    log "Pushed next iteration commit ${next_version}."
  else
    log "Next iteration prepared locally as ${next_version}. Run 'git push' when ready."
  fi
}

prepare_next_iteration() {
  local next_version="$1"
  validate_snapshot_version "$next_version"
  require_clean_git

  log "Setting next iteration version ${next_version}"
  set_revision "$next_version"
  set_jreleaser_version "$next_version"

  commit_changes "chore: prepare next iteration ${next_version}" true
  log "Next iteration commit created."
}

main() {
  local cmd="${1:-}"
  case "$cmd" in
    update-deps)
      local do_commit="${2:-}"
      if [[ -n "$do_commit" && "$do_commit" != "--commit" ]]; then
        die "update-deps accepts only optional --commit"
      fi
      if [[ "$do_commit" == "--commit" ]]; then
        require_clean_git
      fi
      run_update_format_and_regression_for_all
      if [[ "$do_commit" == "--commit" ]]; then
        commit_update_deps_changes
      fi
      ;;
    prepare-release)
      [[ $# -eq 2 ]] || die "prepare-release requires <version>"
      prepare_release "$2"
      ;;
    release)
      [[ $# -ge 2 && $# -le 3 ]] || die "release requires <version> [--push]"
      release_version "$2" "${3:-}"
      ;;
    release-cycle)
      [[ $# -ge 2 && $# -le 3 ]] || die "release-cycle requires <version> [--push]"
      release_cycle "$2" "${3:-}"
      ;;
    prepare-next)
      [[ $# -eq 2 ]] || die "prepare-next requires <next-snapshot-version>"
      prepare_next_iteration "$2"
      ;;
    -h|--help|help|"")
      usage
      ;;
    *)
      die "Unknown command: $cmd"
      ;;
  esac
}

main "$@"
