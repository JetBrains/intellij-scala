package org.jetbrains.sbt.process.options

import com.intellij.execution.configuration.EnvironmentVariablesData
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
 * - [[UnrecognizedSbtOptionsReporter]]
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
      """.stripMargin
    val expected = Seq(
      "-Xmx2G",
      "-Dhoodlump=bloom"
    )

    writeJvmOptsToFileInDir(input)
    val actual = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      vmOptionsFromSettings = Seq.empty,
      environmentVariables = envData()
    )

    assertEquals(expected, actual)
  }

  @Test
  def resolveJavaOptionsCollectsEnvironmentOnly(): Unit = {
    val expected = Seq(
      "-Dfrom.env=true",
      "-Xmx1G"
    )
    val actual = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      vmOptionsFromSettings = Seq.empty,
      environmentVariables = envData(
        userEnvironment = Map("JAVA_OPTS" -> "-Dfrom.env=true ignored -Xmx1G # -Dignored=true")
      )
    )

    assertEquals(expected, actual)
  }

  @Test
  def resolveJavaOptionsCollectsEnvFileAndSettingsInOrder(): Unit = {
    writeJvmOptsToFileInDir(
      """
        |-Dfrom.file=true
        |ignored
        |-Xms256M
        |""".stripMargin
    )

    val expected =
      Seq(
        "-Dfrom.env=true",
        "-Xmx1G",
        "-Dfrom.file=true",
        "-Xms256M",
        "-Dfrom.settings=true"
      )
    val actual = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      vmOptionsFromSettings = Seq("-Dfrom.settings=true"),
      environmentVariables = envData(
        passParentEnvironment = true,
        userEnvironment = Map("JAVA_OPTS" -> "-Dfrom.env=true ignored -Xmx1G")
      )
    )

    assertEquals(expected, actual)
  }

  @Test
  def resolveJavaOptionsCollectsSettingsOnlyWhenEnvAndFileAreAbsent(): Unit = {
    val expected = Seq("-Dfrom.settings=true")
    val actual = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      vmOptionsFromSettings = Seq("-Dfrom.settings=true"),
      environmentVariables = envData()
    )

    assertEquals(expected, actual)
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
    writeSbtOptsToFileInDir("-unknown-from-file")

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
          "unrecognized sbt option: -unknown-from-env",
          s"""Unrecognized sbt option: -unknown-from-env (Source: SBT_OPTS environment variable)
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "unrecognized sbt option: -unknown-from-file",
          s"""Unrecognized sbt option: -unknown-from-file (Source: .sbtopts file)
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "unrecognized sbt option: -unknown-from-settings",
          s"""Unrecognized sbt option: -unknown-from-settings (Source: IDE settings)
             |$AllAvailableOptionsText""".stripMargin
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
      sbtOptionsFromSettings = SbtProcessOptionsResolver.parseSbtOptionsFromSettings(rawOptions)
    ).sbtLauncherArgs

    val expected = Seq("--debug", "--warn")
    assertEquals("Inline comments in .sbtopts should not discard following lines", expected, fromFile)
    assertEquals("Inline comments in SBT_OPTS should not discard following lines", expected, fromEnvironment)
    assertEquals("Inline comments in IDE settings should not discard following lines", expected, fromSettings)
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
}
