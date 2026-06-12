package org.jetbrains.sbt.process.options

import com.intellij.execution.configuration.EnvironmentVariablesData
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.PathTestUtil.tempPathReleasable
import org.jetbrains.sbt.process.options.utils.MessagesCollectingBuildReporter
import org.jetbrains.sbt.process.options.utils.SbtOptionsWarningAssertions.{AllAvailableOptionsText, WarningData, assertNoWarnings, assertWarnings}
import org.junit.Assert.assertEquals
import org.junit.{After, Before, Test}

import java.nio.file.{Files, Path}
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.MapHasAsJava

/**
 * Boundary-level coverage for [[SbtProcessOptionsResolver]] source collection and process-target rendering.
 *
 * Some cases resolve only JVM options or only sbt options to cover source loading without testing collector internals
 * directly.
 *
 * ## Test coverage
 * Primary coverage:
 * - [[SbtProcessOptionsResolver]]
 * - [[SbtProcessOptions]]
 *
 * Indirect coverage:
 * - [[SbtProcessOptionsRenderer]]
 * - [[SbtOptionsDiagnosticsReporter]]
 * - [[collecting.SbtOptionsCollector]]
 * - [[collecting.JvmOptionsCollector]]
 * - [[parsing.SbtOptionsTextNormalizer]]
 * - [[parsing.SbtOptionsParser]]
 * - [[parsing.CommentsAndQuotesPreprocessor]]
 * - [[knownOptions.KnownSbtOptions]]
 */
class SbtProcessOptionsResolverTest {

  private var workingDir: Path = uninitialized

  @Before
  def setUp(): Unit = {
    workingDir = Files.createTempDirectory("sbtProcessOptionsResolverTest")
  }

  @After
  def tearDown(): Unit = {
    tempPathReleasable.release(workingDir)
    workingDir = null
  }

  @Test
  def resolveAllOptionsKeepsJvmOptionsAndLauncherArgsSeparate(): Unit = {
    val expected = SbtProcessOptions(
      allVmOptions = Seq("-Xmx2G", "-Dsbt.global.base=/tmp/sbt-dir"),
      sbtLauncherArgs = Seq("--debug", "reload")
    )
    val reporter = new MessagesCollectingBuildReporter
    val actual = SbtProcessOptionsResolver.resolveForSeparateProcess(
      workingDir,
      vmOptionsFromSettings = Seq("-Xmx2G"),
      sbtOptionsFromSettings = Seq("-debug", "-sbt-dir /tmp/sbt-dir", "-color=always"),
      environmentVariables = envData(),
      additionalLauncherArgs = Seq("reload")
    )(using reporter)

    assertProcessOptionsEquals(expected, actual)
    assertNoWarnings(reporter)
  }

  // These JVM option source cases indirectly cover JvmOptionsCollector.

  @Test
  def resolveJavaOptionsCollectsFileOnly(): Unit = {
    val input =
      """
        |# My jvm options
        |-Xmx2G # -Dsbt.color=always
        |-Dhoodlump=bloom
        |--add-exports java.base/sun.nio.ch=ALL-UNNAMED
        |--add-modules
        |java.base
      """.stripMargin
    val expected = Seq(
      "-Xmx2G",
      "-Dhoodlump=bloom",
      "--add-exports",
      "java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-modules",
      "java.base"
    )

    writeJvmOptsToFileInDir(input)
    val actual = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      vmOptionsFromSettings = Seq.empty,
      environmentVariables = envData()
    )

    assertCollectionEquals(expected, actual)
  }

  @Test
  def resolveJavaOptionsCollectsEnvironmentOnly(): Unit = {
    val expected = Seq(
      "-Dfrom.env=true",
      "standalone-env-token",
      "--add-exports",
      "java.base/sun.nio.ch=ALL-UNNAMED",
      "-Xmx1G"
    )
    val actual = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      vmOptionsFromSettings = Seq.empty,
      environmentVariables = envData(
        userEnvironment = Map("JAVA_OPTS" -> "-Dfrom.env=true standalone-env-token --add-exports java.base/sun.nio.ch=ALL-UNNAMED -Xmx1G # -Dignored=true")
      )
    )

    assertCollectionEquals(expected, actual)
  }

  @Test
  def resolveJavaOptionsCollectsEnvFileAndSettingsInOrder(): Unit = {
    writeJvmOptsToFileInDir(
      """
        |-Dfrom.file=true
        |standalone-file-token
        |--add-modules
        |java.base
        |-Xms256M
        |""".stripMargin
    )

    val expected =
      Seq(
        "-Dfrom.env=true",
        "standalone-env-token",
        "-Xmx1G",
        "-Dfrom.file=true",
        "standalone-file-token",
        "--add-modules",
        "java.base",
        "-Xms256M",
        "-Dfrom.settings=true"
      )
    val actual = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      vmOptionsFromSettings = Seq("-Dfrom.settings=true"),
      environmentVariables = envData(
        passParentEnvironment = true,
        userEnvironment = Map("JAVA_OPTS" -> "-Dfrom.env=true standalone-env-token -Xmx1G")
      )
    )

    assertCollectionEquals(expected, actual)
  }

  @Test
  def resolveJavaOptionsCollectsSettingsOnlyWhenEnvAndFileAreAbsent(): Unit = {
    val expected = Seq("-Dfrom.settings=true")
    val actual = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      vmOptionsFromSettings = Seq("-Dfrom.settings=true"),
      environmentVariables = envData()
    )

    assertCollectionEquals(expected, actual)
  }

  // These sbt option source cases indirectly cover SbtOptionsCollector.

  @Test
  def resolveSbtOptionsCollectsFileOnly(): Unit = {
    val input =
      """
        |--sbt-boot /some/where/sbt/boot -sbt-dir      /some/where/else/sbt
        |--ivy /some/where/ivy -color=   always -debug
        |-jvm-debug 4711
        |--d
        |-error
        |debug
        |--warn
        |--debug
        |
      """.stripMargin
    val expected = SbtProcessOptions(
      allVmOptions = Seq(
        "-Dsbt.boot.directory=/some/where/sbt/boot",
        "-Dsbt.global.base=/some/where/else/sbt",
        "-Dsbt.ivy.home=/some/where/ivy",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=4711"
      ),
      sbtLauncherArgs = Seq("--debug", "--error", "--warn", "--debug")
    )

    writeSbtOptsToFileInDir(input)
    assertEquals(expected, resolveSeparateProcessSbtOptions())
  }

  @Test
  def resolveSbtOptionsCollectsFileWithComments(): Unit = {
    val input =
      """
        |#--sbt-boot /some/where/sbt/boot -sbt-dir      /some/where/else/sbt
        |--ivy /some/where/ivy -color=   always -debug #-jvm-debug 4711
        |--d "#" -error
        |debug #--sbt-boot /some/where/sbt/boot
        |--warn
        |--debug
        |
      """.stripMargin
    val expected = SbtProcessOptions(
      allVmOptions = Seq("-Dsbt.ivy.home=/some/where/ivy"),
      sbtLauncherArgs = Seq("--debug", "--error", "--warn", "--debug")
    )

    writeSbtOptsToFileInDir(input)
    assertEquals(expected, resolveSeparateProcessSbtOptions())
  }

  @Test
  def resolveSbtOptionsCollectsEnvironmentOnly(): Unit = {
    val expected = SbtProcessOptions(
      allVmOptions = Seq(
        "-Dsbt.boot.directory=/env/boot",
        "-Xmx1G"
      ),
      sbtLauncherArgs = Seq("--debug")
    )

    assertEquals(
      expected,
      resolveSeparateProcessSbtOptions(
        environmentVariables = envData(userEnvironment = Map("SBT_OPTS" -> "-sbt-boot /env/boot -debug -J-Xmx1G"))
      )
    )
  }

  @Test
  def resolveSbtOptionsCollectsEnvFileAndSettingsInOrder(): Unit = {
    writeSbtOptsToFileInDir(
      """
        |-ivy /file/ivy
        |-warn
        |""".stripMargin
    )

    val expectedShellOptions =
      SbtProcessOptions(
        allVmOptions = Seq(
          "-Dsbt.boot.directory=/env/boot",
          "-Dsbt.color=always",
          "-Dsbt.ivy.home=/file/ivy",
          s"-Dsbt.global.base=${workingDir.toAbsolutePath.normalize()}/project/.sbtboot"
        ),
        sbtLauncherArgs = Seq("--debug", "--warn", "--error")
      )
    val expectedSeparateProcessOptions =
      SbtProcessOptions(
        allVmOptions = Seq(
          "-Dsbt.boot.directory=/env/boot",
          "-Dsbt.ivy.home=/file/ivy",
          s"-Dsbt.global.base=${workingDir.toAbsolutePath.normalize()}/project/.sbtboot"
        ),
        sbtLauncherArgs = Seq("--debug", "--warn", "--error")
      )
    val shellReporter = new MessagesCollectingBuildReporter
    val separateProcessReporter = new MessagesCollectingBuildReporter
    val actualShellOptions = SbtProcessOptionsResolver.resolveSbtOptionsForShell(
      workingDir = workingDir,
      sbtOptionsFromSettings = Seq("-error", "-no-global"),
      environmentVariables = envData(
        passParentEnvironment = true,
        userEnvironment = Map("SBT_OPTS" -> "-debug -sbt-boot /env/boot -color=always")
      )
    )(using shellReporter)
    val actualSeparateProcessOptions = SbtProcessOptionsResolver.resolveSbtOptionsForSeparateProcess(
      workingDir = workingDir,
      sbtOptionsFromSettings = Seq("-error", "-no-global"),
      environmentVariables = envData(
        passParentEnvironment = true,
        userEnvironment = Map("SBT_OPTS" -> "-debug -sbt-boot /env/boot -color=always")
      )
    )(using separateProcessReporter)

    assertEquals(expectedShellOptions, actualShellOptions)
    assertEquals(expectedSeparateProcessOptions, actualSeparateProcessOptions)
    assertNoWarnings(shellReporter)
    assertNoWarnings(separateProcessReporter)
  }

  @Test
  def resolveSbtOptionsCollectsRawJvmOptionsFromAllSourcesInOrder(): Unit = {
    writeSbtOptsToFileInDir(
      """
        |-ivy /file/ivy
        |-warn
        |-J-Xms256M
        |""".stripMargin
    )

    val expected = SbtProcessOptions(
      allVmOptions = Seq(
        "-Dsbt.boot.directory=/env/boot",
        "-Xmx1G",
        "-Dsbt.ivy.home=/file/ivy",
        "-Xms256M",
        "-Dsbt.global.base=/settings/sbt",
        "-Dfrom.settings=true"
      ),
      sbtLauncherArgs = Seq("--debug", "--warn", "--error")
    )

    assertEquals(
      expected,
      resolveSeparateProcessSbtOptions(
        sbtOptionsFromSettings = Seq("-sbt-dir /settings/sbt", "-error", "-J-Dfrom.settings=true"),
        environmentVariables = envData(userEnvironment = Map("SBT_OPTS" -> "-sbt-boot /env/boot -debug -J-Xmx1G"))
      )
    )
  }

  @Test
  def resolveSbtOptionsReportsUnrecognisedOptionsFromAllSources(): Unit = {
    writeSbtOptsToFileInDir(
      """
        |-unknown-from-file
        |""".stripMargin
    )

    val reporter = new MessagesCollectingBuildReporter
    val actual = SbtProcessOptionsResolver.resolveSbtOptionsForSeparateProcess(
      workingDir = workingDir,
      sbtOptionsFromSettings = Seq("-unknown-from-settings"),
      environmentVariables = envData(userEnvironment = Map("SBT_OPTS" -> "-unknown-from-env"))
    )(using reporter)

    assertEquals(SbtProcessOptions(Seq.empty, Seq.empty), actual)
    assertWarnings(
      reporter,
      Seq(
        WarningData(
          "unrecognized sbt option: -unknown-from-env (SBT_OPTS environment variable)",
          s"""Unrecognized sbt option: -unknown-from-env.
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "unrecognized sbt option: -unknown-from-file (.sbtopts file)",
          s"""Unrecognized sbt option: -unknown-from-file at:
             |${sbtOptsFilePath}:2
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "unrecognized sbt option: -unknown-from-settings (IDE settings)",
          s"""Unrecognized sbt option: -unknown-from-settings.
             |<a href="open_sbt_settings">Open Settings</a>
             |$AllAvailableOptionsText""".stripMargin
        )
      )
    )
  }

  @Test
  def resolveSbtOptionsReportsMalformedEnvironmentOptions(): Unit = {
    val reporter = new MessagesCollectingBuildReporter
    val actual = SbtProcessOptionsResolver.resolveSbtOptionsForSeparateProcess(
      workingDir = workingDir,
      sbtOptionsFromSettings = Seq.empty,
      environmentVariables = envData(userEnvironment = Map("SBT_OPTS" -> """-sbt-dir "/env/sbt"""))
    )(using reporter)

    assertEquals(SbtProcessOptions(Seq.empty, Seq.empty), actual)
    assertWarnings(
      reporter,
      Seq(
        WarningData(
          "malformed sbt option: -sbt-dir (SBT_OPTS environment variable)",
          "Malformed sbt option input: unbalanced double quote on line 1"
        )
      )
    )
  }

  @Test
  def resolveSbtOptionsReportsMalformedFileLineAndKeepsOtherLines(): Unit = {
    writeSbtOptsToFileInDir(
      """
        |-debug
        |-sbt-dir "/file/sbt
        |-warn
        |-ivy '/file/ivy
        |""".stripMargin
    )

    val reporter = new MessagesCollectingBuildReporter
    val actual = SbtProcessOptionsResolver.resolveSbtOptionsForSeparateProcess(
      workingDir = workingDir,
      sbtOptionsFromSettings = Seq.empty,
      environmentVariables = envData()
    )(using reporter)

    assertEquals(SbtProcessOptions(Seq.empty, Seq("--debug", "--warn")), actual)
    assertWarnings(
      reporter,
      Seq(
        WarningData(
          "malformed sbt option: -sbt-dir (.sbtopts file)",
          s"""Malformed sbt option input: unbalanced double quote at:
             |${sbtOptsFilePath}:3
             |-sbt-dir "/file/sbt""".stripMargin
        ),
        WarningData(
          "malformed sbt option: -ivy (.sbtopts file)",
          s"""Malformed sbt option input: unbalanced single quote at:
             |${sbtOptsFilePath}:5
             |-ivy '/file/ivy""".stripMargin
        )
      )
    )
  }

  @Test
  def resolveSbtOptionsReportsMalformedIdeSettingsOptions(): Unit = {
    val parsedSettings = SbtProcessOptionsResolver.parseSbtOptionsFromSettings("""-sbt-dir "/settings/sbt""")

    val reporter = new MessagesCollectingBuildReporter
    val actual = SbtProcessOptionsResolver.resolveSbtOptionsForSeparateProcess(
      workingDir = workingDir,
      sbtOptionsFromSettings = parsedSettings.options,
      environmentVariables = envData(),
      malformedSbtOptionsFromSettings = parsedSettings.malformedOptions
    )(using reporter)

    assertEquals(SbtProcessOptions(Seq.empty, Seq.empty), actual)
    assertWarnings(
      reporter,
      Seq(
        WarningData(
          "malformed sbt option: -sbt-dir (IDE settings)",
          """Malformed sbt option input: unbalanced double quote on line 1
            |<a href="open_sbt_settings">Open Settings</a>""".stripMargin
        )
      )
    )
  }

  @Test
  def resolveSbtOptionsReportsAllMalformedOptionsFromAllSources(): Unit = {
    writeSbtOptsToFileInDir(
      """-sbt-dir "/file/sbt
        |-ivy '/file/ivy""".stripMargin
    )
    val parsedSettings = SbtProcessOptionsResolver.parseSbtOptionsFromSettings(
      """-sbt-dir "/settings/sbt
        |-ivy '/settings/ivy""".stripMargin
    )

    val reporter = new MessagesCollectingBuildReporter
    val actual = SbtProcessOptionsResolver.resolveSbtOptionsForSeparateProcess(
      workingDir = workingDir,
      sbtOptionsFromSettings = parsedSettings.options,
      environmentVariables = envData(userEnvironment = Map(
        "SBT_OPTS" ->
          """-sbt-dir "/env/sbt
            |-ivy '/env/ivy""".stripMargin
      )),
      malformedSbtOptionsFromSettings = parsedSettings.malformedOptions
    )(using reporter)

    assertEquals(SbtProcessOptions(Seq.empty, Seq.empty), actual)
    assertWarnings(
      reporter,
      Seq(
        WarningData(
          "malformed sbt option: -sbt-dir (SBT_OPTS environment variable)",
          "Malformed sbt option input: unbalanced double quote on line 1"
        ),
        WarningData(
          "malformed sbt option: -ivy (SBT_OPTS environment variable)",
          "Malformed sbt option input: unbalanced single quote on line 2"
        ),
        WarningData(
          "malformed sbt option: -sbt-dir (.sbtopts file)",
          s"""Malformed sbt option input: unbalanced double quote at:
             |${sbtOptsFilePath}:1
             |-sbt-dir "/file/sbt""".stripMargin
        ),
        WarningData(
          "malformed sbt option: -ivy (.sbtopts file)",
          s"""Malformed sbt option input: unbalanced single quote at:
             |${sbtOptsFilePath}:2
             |-ivy '/file/ivy""".stripMargin
        ),
        WarningData(
          "malformed sbt option: -sbt-dir (IDE settings)",
          """Malformed sbt option input: unbalanced double quote on line 1
            |<a href="open_sbt_settings">Open Settings</a>""".stripMargin
        ),
        WarningData(
          "malformed sbt option: -ivy (IDE settings)",
          """Malformed sbt option input: unbalanced single quote on line 2
            |<a href="open_sbt_settings">Open Settings</a>""".stripMargin
        )
      )
    )
  }

  @Test
  def resolveSbtOptionsCollectsSettingsOnlyWhenEnvAndFileAreAbsent(): Unit = {
    val expected = SbtProcessOptions(
      allVmOptions = Seq(
        "-Dsbt.global.base=/settings/sbt",
        "-Dxsbt.inc.debug=true"
      ),
      sbtLauncherArgs = Seq("--error")
    )

    val actual = resolveSeparateProcessSbtOptions(
      sbtOptionsFromSettings = Seq("-sbt-dir /settings/sbt", "-debug-inc", "-error")
    )

    assertEquals(expected, actual)
  }

  @Test
  def resolveSbtOptionsWithInlineCommentsKeepsFollowingLinesForAllSources(): Unit = {
    val rawOptions =
      """|-debug # comment
         |-warn
         |""".stripMargin

    writeSbtOptsToFileInDir(rawOptions)
    val fromFile = resolveSeparateProcessSbtOptions().sbtLauncherArgs

    Files.deleteIfExists(workingDir.resolve(".sbtopts"))
    val fromEnvironment = resolveSeparateProcessSbtOptions(
      environmentVariables = envData(userEnvironment = Map("SBT_OPTS" -> rawOptions))
    ).sbtLauncherArgs

    val fromSettings = resolveSeparateProcessSbtOptions(
      sbtOptionsFromSettings = SbtProcessOptionsResolver.parseSbtOptionsFromSettings(rawOptions).options
    ).sbtLauncherArgs

    val expected = Seq("--debug", "--warn")
    assertCollectionEquals("Inline comments in .sbtopts should not discard following lines", expected, fromFile)
    assertCollectionEquals("Inline comments in SBT_OPTS should not discard following lines", expected, fromEnvironment)
    assertCollectionEquals("Inline comments in IDE settings should not discard following lines", expected, fromSettings)
  }

  @Test
  def resolveAllOptionsReturnsOnlyAdditionalLauncherArgsWhenSbtLauncherOptionsAreAbsent(): Unit = {
    val expected = SbtProcessOptions(
      allVmOptions = Seq.empty,
      sbtLauncherArgs = Seq("compile")
    )
    val reporter = new MessagesCollectingBuildReporter
    val actual = SbtProcessOptionsResolver.resolveForSeparateProcess(
      workingDir,
      vmOptionsFromSettings = Seq.empty,
      sbtOptionsFromSettings = Seq.empty,
      environmentVariables = envData(),
      additionalLauncherArgs = Seq("compile")
    )(using reporter)

    assertProcessOptionsEquals(expected, actual)
    assertNoWarnings(reporter)
  }

  private def assertProcessOptionsEquals(expected: SbtProcessOptions, actual: SbtProcessOptions): Unit =
    assertEquals(
      renderProcessOptions(expected),
      renderProcessOptions(actual)
    )

  private def envData(
    passParentEnvironment: Boolean = false,
    userEnvironment: Map[String, String] = Map.empty
  ): EnvironmentVariablesData =
    EnvironmentVariablesData.create(userEnvironment.asJava, passParentEnvironment)

  private def renderProcessOptions(options: SbtProcessOptions): String =
    s"""SbtProcessOptions:
       |  allVmOptions:
       |${renderValues(options.allVmOptions)}
       |  sbtLauncherArgs:
       |${renderValues(options.sbtLauncherArgs)}
       |""".stripMargin

  private def renderValues(values: Seq[String]): String =
    if (values.isEmpty)
      "    <empty>"
    else
      values.map(value => s"    - $value").mkString("\n")

  private def resolveSeparateProcessSbtOptions(
    sbtOptionsFromSettings: Seq[String] = Seq.empty,
    environmentVariables: EnvironmentVariablesData = envData()
  ): SbtProcessOptions =
    SbtProcessOptionsResolver.resolveSbtOptionsForSeparateProcess(
      workingDir = workingDir,
      sbtOptionsFromSettings = sbtOptionsFromSettings,
      environmentVariables = environmentVariables
    )(using null)

  private def writeJvmOptsToFileInDir(text: String): Unit =
    Files.writeString(workingDir.resolve(".jvmopts"), text)

  private def writeSbtOptsToFileInDir(text: String): Unit =
    Files.writeString(workingDir.resolve(".sbtopts"), text)

  private def sbtOptsFilePath: String =
    workingDir.resolve(".sbtopts").toAbsolutePath.toUri.toString
}
