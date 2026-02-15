# Contributing

## Development Flow

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Ensure you have added suitable tests and the test suite is passing(`./gradlew check`)
5. Push to the branch (`git push origin my-new-feature`)
6. Create a new Pull Request

### Building

A gradle wrapper is included, so these tasks can run without any prior installation of gradle. The Linux/OSX form of the commands, given
below, is:

```bash
./gradlew <task name>
```

For Windows, use the batch file:

```cmd
gradlew.bat <task name>
```

To build the library’s AAR package, execute:

```bash
./gradlew chat:assemble
```

The built AAR package will be located in the `chat/build/outputs/aar/` directory.

### Code Standard

#### Detekt

We use [Detekt](https://detekt.dev/) for code style checks and static analysis in this project.
Detekt helps maintain a consistent codebase and identify potential issues in the code.
Before submitting a pull request, ensure that your code passes all Detekt checks.

#### Running Detekt Locally

You can run Detekt locally to verify your code against the configured rules. Use the following command:

```bash
./gradlew detekt
```

#### Updating the rules

Detekt’s rules can be updated by modifying the [detekt.yml](https://github.com/ably/ably-chat-kotlin/blob/main/detekt.yml) configuration
file. Ensure that any changes to the rules are well-documented and align with the project’s coding standards.

### Running Tests

Our tests are written using JUnit and can be executed with the following command:

```bash
./gradlew test
```

This will run all unit tests in the project. Ensure all tests pass before submitting a pull request.

## Using `chat` locally in other projects

If you wish to make changes to the source code and test them immediately in another project, follow these steps:

### Step 1: Update the Version Property

Modify the version property in `gradle.properties` to reflect a local version:

```properties
VERSION_NAME=0.1.0-local.1
```

### Step 2: Publish to Maven Local

Publish the library to your local Maven repository by running:

```bash
./gradlew publishToMavenLocal -PskipSigning
```

> [!TIP]
> The `-PskipSigning` flag skips GPG signing which is only required for Maven Central releases.
> If you have GPG signing configured (see [Prerequisites for Release](#prerequisites-for-release)), you can omit this flag.

### Step 3: Use the Local Version in Another Project

In the project where you want to use the updated library:

1. Add the `mavenLocal()` repository to your `settings.gradle.kts` (or `settings.gradle`) file. It should be added **before** other repositories to ensure local versions take precedence:
   ```kotlin
   dependencyResolutionManagement {
       repositories {
           mavenLocal() // Add this first to prioritize local versions
           google()
           mavenCentral()
       }
   }
   ```

2. Update the dependency version to match your local version. If using a version catalog (`gradle/libs.versions.toml`):
   ```toml
   [versions]
   ablyChat = "0.1.0-local.1"
   ```

   Or directly in your module's `build.gradle.kts`:
   ```kotlin
   implementation("com.ably.chat:chat:0.1.0-local.1")
   ```

3. Sync your project to pull the local dependency.

> [!NOTE]
> - Ensure the version number (`0.1.0-local.1`) matches the one you set in `gradle.properties`.
> - The `mavenLocal()` repository should typically be used only during development to avoid conflicts with published versions in remote repositories.
> - Remember to remove `mavenLocal()` and revert the version before committing your changes.

## Documentation

The source of truth for documentation for the Ably Chat SDKs can be found on the [Ably Docs repository](https://github.com/ably/docs).

Please ensure that you merge any pull requests in that repository promptly after releasing your change.

Any releases must be accompanied by a PR to bump the library install version in the setup/install guide, at minimum.

## Release Process

### Prerequisites for Release

Before starting the release process, ensure you have:

1. Sonatype OSSRH account credentials configured in your `~/.gradle/gradle.properties`:
   ```properties
   mavenCentralUsername=user-token-username
   mavenCentralPassword=user-token-password
   ```
2. GPG key for signing artifacts:

- Generate a key pair if you don't have one: `gpg --gen-key`
- Export the secret key to gradle.properties:
    ```properties
    signing.keyId=short-key-id
    signing.password=key-password
    signing.secretKeyRingFile=/path/to/.gnupg/secring.gpg
    ```

## Release Process

This library uses [semantic versioning](http://semver.org/). For each release, the following needs to be done:

1. Create a branch for the release, named like `release/1.2.4` (where `1.2.4` is what you're releasing, being the new version)
2. Replace all references of the current version number with the new version number (check the [README.md](./README.md)
   and [gradle.properties](./gradle.properties)) and commit the changes
3. Run [`github_changelog_generator`](https://github.com/github-changelog-generator/github-changelog-generator) to automate the update of
   the [CHANGELOG](./CHANGELOG.md). This may require some manual intervention, both in terms of how the command is run and how the change
   log file is modified. Your mileage may vary:

    - The command you will need to run will look something like this:
      `github_changelog_generator -u ably -p ably-chat-kotlin --since-tag v1.2.3 --output delta.md --token $GITHUB_TOKEN_WITH_REPO_ACCESS`.
      Generate token [here](https://github.com/settings/tokens/new?description=GitHub%20Changelog%20Generator%20token).
    - Using the command above, `--output delta.md` writes changes made after `--since-tag` to a new file.
    - The contents of that new file (`delta.md`) then need to be manually inserted at the top of the `CHANGELOG.md`, changing the "
      Unreleased"
      heading and linking with the current version numbers.
    - Also ensure that the "Full Changelog" link points to the new version tag instead of the `HEAD`.

4. Commit [CHANGELOG](./CHANGELOG.md)
5. Create a PR on the [website docs](https://github.com/ably/docs), [website snippets](https://github.com/ably/website) and
[voltaire snippets](https://github.com/ably/voltaire/) that updates that SDK version in the setup/installation guide.
6. Make a PR against `main`
7. Once the PR is approved, merge it into `main`
8. Add a tag to the new `main` head commit and push to origin such as `git tag v1.2.4 && git push origin v1.2.4`
9. Visit [https://github.com/ably/ably-chat-kotlin/tags](https://github.com/ably/ably-chat-kotlin/tags) and add release notes for the release including links to the changelog entry.
10. Use the [GitHub action](https://github.com/ably/ably-chat-kotlin/actions/workflows/release.yaml) to publish the release. Run the workflow on the release tag created in Step 8.
11. Merge any [website docs](https://github.com/ably/docs) PRs related to the changes, including the one you created in Step 5.
12. Create the entry on the [Ably Changelog](https://changelog.ably.com/) (via [headwayapp](https://headwayapp.co/))
