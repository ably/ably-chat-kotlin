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
> If you have GPG signing configured (see [Prerequisites for Publishing to Maven Central](#prerequisites-for-publishing-to-maven-central)), you can omit this flag.

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

## Validate website docs snippet changes

As part of the [release process](#release-process) (see Step 5), you should validate that the web documentation code snippets are accurate
and up-to-date with the SDK source code. This is done by running the following prompt against a locally cloned copy of the
[ably/docs](https://github.com/ably/docs) repository and this SDK repository.

> [!IMPORTANT]
> This prompt should be run with the most powerful LLM available to you (e.g. Claude Opus, GPT-4, etc.) for the best results.

Replace `{DOCS_PATH}` with the path to your local clone of the [ably/docs](https://github.com/ably/docs) repository, `{SDK_NAME}` with
`ably-chat-kotlin`, and `{SDK_PATH}` with the path to your local clone of this SDK repository.

~~~
Verify all `kotlin` and `android` annotated code snippets in `.mdx` files located at `{DOCS_PATH}` against the `{SDK_NAME}` source code repository at `{SDK_PATH}`.

### Verification Steps:

1. **Find all code snippets**: Search for all code blocks with the `kotlin` and `android` annotation in `.mdx` files.

2. **Understand SDK structure**: Analyze the SDK source code to understand:
   - Public classes and their constructors
   - Public methods and their signatures (parameters, return types)
   - Public properties and their types
   - Enums and their values
   - Namespaces and import requirements

3. **Cross-check each snippet** for the following issues:
   - **Syntax errors**: Missing keywords (e.g., `new` for constructors), missing semicolons, incorrect brackets
   - **Naming conventions**: Verify casing matches the language conventions (e.g., PascalCase for C# properties, camelCase for JavaScript)
   - **API accuracy**: Verify method names, property names, and enum values exist in the SDK
   - **Type correctness**: Verify correct types are used (e.g., `ConnectionEvent` vs `ConnectionState`)
   - **Namespace/import requirements**: Note any required imports that are missing from examples
   - **Wrong language**: Detect if code from another language was accidentally used

4. **Generate a verification report** with:
   - Total snippets found
   - List of issues found with:
     - File path and line number
     - Current (incorrect) code
     - Expected (correct) code
     - Source reference in SDK
   - List of verified APIs that are correct
   - Success rate percentage
   - Recommendations for fixes

### Output Format:
Create/update a markdown report file `chat_kotlin_api_verification_report.md` with all findings.
~~~

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
5. Run the [Validate website docs snippet changes](#validate-website-docs-snippet-changes) locally to verify that all `kotlin` and `android` code
snippets in the [web documentation](https://github.com/ably/docs) are accurate and up-to-date with the SDK source code. Review the generated report and fix any issues found.
6. Create a PR on the [website docs](https://github.com/ably/docs), [website snippets](https://github.com/ably/website) and
[voltaire snippets](https://github.com/ably/voltaire/) that updates the SDK version in the setup/installation guide. Additionally, include fixes for any documentation issues identified in Step 5. Even if there are no public API changes, a PR must still be created to update the
SDK version.
7. Make a PR against `main`
8. Once the PR is approved, merge it into `main`
9. Add a tag to the new `main` head commit and push to origin such as `git tag v1.2.4 && git push origin v1.2.4`
10. Visit [https://github.com/ably/ably-chat-kotlin/tags](https://github.com/ably/ably-chat-kotlin/tags) and add release notes for the release including links to the changelog entry.
11. Use the [GitHub action](https://github.com/ably/ably-chat-kotlin/actions/workflows/release.yaml) to publish the release. Run the workflow on the release tag created in Step 9.
12. Merge any [website docs](https://github.com/ably/docs) PRs related to the changes, including the one you created in Step 6.
13. Create the entry on the [Ably Changelog](https://changelog.ably.com/) (via [headwayapp](https://headwayapp.co/))

### Publishing to Maven Central

Ensure you have the following configured in your `~/.gradle/gradle.properties`:

1. Sonatype OSSRH account credentials:
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
