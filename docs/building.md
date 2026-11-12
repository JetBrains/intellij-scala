# Building the Scala plugin
This guide owns the shared build prerequisites and general sbt guidance for Community and Ultimate checkouts.
Use the guide and build configuration from the branch you are working on.

## Prerequisites
### JDK
Build JDK: **JDK 25**.

Use this JDK to launch sbt and as the project JDK in IntelliJ IDEA. You can
[download it through IntelliJ IDEA](https://www.jetbrains.com/help/idea/sdk.html#define-sdk).
For command-line builds, select it through `JAVA_HOME` or sbt's `--java-home` launcher option.

The build declares its minimum JDK in [build.sbt](../build.sbt). Keep this guide and the CI configuration aligned when
that requirement changes; other documentation should link here instead of repeating the version.

### sbt
Have the `sbt` command available locally. The launcher's build-specific version comes from `project/build.properties`;
there is no need to maintain a separate version value in these instructions.

### Test and standalone runtimes
The JDK that launches sbt is separate from the downloaded IntelliJ SDK's JBR used by forked platform tests.
Compatibility tests may also need older JDKs; these do not replace the build JDK.

Standalone builds can have their own requirements. In particular, follow the
[worksheet REPL interface's instructions](../scala/worksheet-repl-interface/README.md#development-notes) when working
in that build, rather than applying the main plugin's JDK requirement to it.

## Build roots and project selection
Run commands from the appropriate checkout root:

| Checkout | Working directory | Main sbt project |
| --- | --- | --- |
| Ultimate | The Ultimate checkout root, above `community/` | `scalaUltimate` |
| Standalone Community | The Community checkout root | `scalaCommunity` |

Starting sbt in Ultimate's nested `community/` directory loads the Community build rather than the Ultimate build.
Standalone builds, such as the repository maintenance tools and worksheet REPL interface, have their own build context;
follow their local documentation.

Select the intended project explicitly, especially when using a reused server. For example, from the Ultimate root:
```bash
sbt --client ";project scalaUltimate; compile"
```
For Community, substitute the main project from the table. Specialized tasks may need a different project scope.

## Client mode or a fresh process
Prefer `sbt --client` for repeated development commands. It connects to the build's existing server or automatically
starts one. The server stays running after the command finishes, so later commands reuse its JVM and loaded build.
Omit `--client` to use a fresh process for isolated validation or changes to the startup environment, JDK, or JVM
options.

A reused server retains its selected project, session settings, and startup environment. Selecting a project does not
reset session settings. Passing new JVM properties or environment variables to a later client does not reconfigure the
running server.

### Idle timeout and shutdown
sbt's default server idle timeout is seven days. The main Community and Ultimate builds support an optional startup
property for a shorter session, for example:
```bash
sbt --client -Dscala.sbt.idleTimeout=30m ";project scalaUltimate; compile"
```
The property accepts positive finite durations and is read in the server JVM when the build loads. Leave it absent to
preserve the existing setting. Keep a session-specific override on the command line instead of saving it to `.jvmopts`
or global configuration. Standalone builds that do not load Community's `build.sbt`, such as `tools/`, do not inherit
this property.

Let the idle timeout handle routine cleanup. Use `sbt --client shutdown` when explicitly stopping the server is
appropriate; subsequent client commands can start a new one.

See sbt's [server configuration documentation](https://www.scala-sbt.org/1.x/docs/sbt-server.html#Configuration).

## Cleaning build outputs
Use `sbt cleanAll` to clean the `target` directories of all subprojects in the main plugin build.
Plain `sbt clean` cleans only the current project's outputs because the subprojects are not aggregated.
