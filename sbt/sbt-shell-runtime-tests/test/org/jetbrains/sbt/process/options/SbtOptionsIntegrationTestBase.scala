package org.jetbrains.sbt.process.options

import org.jetbrains.sbt.SbtRuntimeTestBase
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert.{assertEquals, assertTrue}

import java.nio.file.{Files, Path}
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.MapHasAsJava

/**
 * End-to-end coverage for sbt options passed from settings, environment, and option files into real sbt processes:
 *  - IDE sbt settings via `-sbt-dir`
 *  - `SBT_OPTS` and `JAVA_OPTS` via source-order sentinel properties
 *  - `.sbtopts` via `-sbt-cache`
 *  - `.jvmopts` via a custom `-Dquoted.jvmopts.path=...` JVM property
 *  - shell-only and multi-arg JVM properties via `-color`, `-no-colors`, and `-timings`
 *
 * This indirectly tests
 *  - [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolver]] for settings, environment, and option-file inputs
 *  - other logic in the [[org.jetbrains.sbt.process.options]] package which the resolver transitively uses
 *
 * Unit-level coverage for the same logic lives in:
 *  - [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolverTest]] (+ other tests from the `options` package)
 */
abstract class SbtOptionsIntegrationTestBase extends SbtRuntimeTestBase {

  override def runInDispatchThread(): Boolean =
    false

  protected final val SbtOptionsProjectRelativePath =
    "sbt-shell-runtime-tests/testdata/sbt/shell/quotedPathOptions"

  private var expectedSbtDirPathWithSpaces: String = uninitialized
  private var expectedSbtCachePathWithSpaces: String = uninitialized
  private var expectedJvmOptsPathWithSpaces: String = uninitialized

  private var outputFile: Path = uninitialized

  protected final def configureQuotedPathOptionSourcesBeforeImport(): Unit = {
    val sbtDir = createDirectory("custom sbt dir with spaces")
    val sbtCacheDir = createDirectory("custom sbt cache with spaces")
    val jvmOptsDir = createDirectory("custom jvmopts dir with spaces")

    expectedSbtDirPathWithSpaces = normalize(sbtDir)
    expectedSbtCachePathWithSpaces = normalize(sbtCacheDir)
    expectedJvmOptsPathWithSpaces = normalize(jvmOptsDir)
    outputFile = Files.createTempFile("quoted-path-options", ".txt")

    val sbtSettings = SbtSettings.getInstance(getMyProject)
    sbtSettings.sbtOptions = Seq(
      "-timings",
      "-color=always",
      "-no-colors",
      "-Doption.source.sbt=settings",
      s"""-sbt-dir "$expectedSbtDirPathWithSpaces""""
    ).mkString(" ")
    val vmParametersWithOutputFile = appendVmParameter(
      sbtSettings.vmParameters,
      s"-Dquoted.settings.outputFile=${normalize(outputFile)}"
    )
    sbtSettings.vmParameters = appendVmParameter(
      vmParametersWithOutputFile,
      "-Doption.source.java=settings"
    )
    sbtSettings.sbtEnvironment = Map(
      "JAVA_OPTS" -> "-Doption.source.java=env",
      "SBT_OPTS" -> "-Doption.source.sbt=env"
    ).asJava

    Files.writeString(
      getTestProjectPath.resolve(".sbtopts"),
      s"""-Doption.source.sbt=file
         |-sbt-cache "$expectedSbtCachePathWithSpaces"""".stripMargin
    )

    Files.writeString(
      getTestProjectPath.resolve(".jvmopts"),
      s"""-Doption.source.java=file
         |-Dquoted.jvmopts.path="$expectedJvmOptsPathWithSpaces"
         |--add-exports java.base/sun.nio.ch=ALL-UNNAMED
         |--add-modules
         |java.base""".stripMargin
    )
  }

  protected final def doTestQuotedPathOptionsFromSettingsAndOptionFilesArePassedToSbtProcess(): Unit =
    assertExtractedProperties(Seq(
      "sbt.global.base" -> expectedSbtDirPathWithSpaces,
      "sbt.global.localcache" -> expectedSbtCachePathWithSpaces,
      "quoted.jvmopts.path" -> expectedJvmOptsPathWithSpaces
    ))

  protected final def doTestOptionModelRegressionPropertiesArePassedToSeparateSbtProcess(): Unit =
    assertExtractedProperties(Seq(
      "sbt.task.timings" -> "true",
      "sbt.task.timings.on.shutdown" -> "true",
      "option.source.java" -> "settings",
      "option.source.sbt" -> "settings",
      "sbt.color" -> "<missing>"
    ))

  protected final def doTestOptionModelRegressionPropertiesArePassedToSbtShell(): Unit =
    assertExtractedProperties(Seq(
      "sbt.task.timings" -> "true",
      "sbt.task.timings.on.shutdown" -> "true",
      "option.source.java" -> "settings",
      "option.source.sbt" -> "settings",
      "sbt.color" -> "always",
      "sbt.log.noformat" -> "true"
    ))

  protected final def doTestNoShareAndTimingsOptionsArePassedToSeparateSbtProcess(): Unit = {
    SbtSettings.getInstance(getMyProject).sbtOptions = "-no-share -timings -color=always"
    assertExtractedProperties(Seq(
      "sbt.global.base" -> "project/.sbtboot",
      "sbt.boot.directory" -> "project/.boot",
      "sbt.ivy.home" -> "project/.ivy",
      "sbt.task.timings" -> "true",
      "sbt.task.timings.on.shutdown" -> "true",
      "sbt.color" -> "<missing>"
    ))
  }

  protected def runSettingExtractionTask(taskName: String): Unit

  protected final def readExtractedValue(taskName: String): String = {
    Files.deleteIfExists(outputFile)
    runSettingExtractionTask(taskName)

    assertTrue(
      s"Task '$taskName' did not write the expected output file: $outputFile",
      Files.exists(outputFile)
    )

    Files.readString(outputFile).trim
  }

  private def assertExtractedProperties(expected: Seq[(String, String)]): Unit = {
    val actual = readExtractedProperties("writeOptionModelRegressionProperties")
    val actualSubset = expected.map { case (propertyName, _) =>
      propertyName -> actual.getOrElse(propertyName, "<missing>")
    }

    assertEquals(renderProperties(expected), renderProperties(actualSubset))
  }

  private def readExtractedProperties(taskName: String): Map[String, String] = {
    val text = readExtractedValue(taskName)
    text.linesIterator.map { line =>
      val separatorIndex = line.indexOf('=')
      assertTrue(s"Malformed property line from task '$taskName': $line", separatorIndex >= 0)
      line.substring(0, separatorIndex) -> line.substring(separatorIndex + 1)
    }.toMap
  }

  private def renderProperties(properties: Seq[(String, String)]): String =
    properties.map { case (propertyName, value) => s"$propertyName=$value" }.mkString("\n")

  private def appendVmParameter(existingParameters: String, newParameter: String): String =
    Seq(Option(existingParameters).getOrElse(""), newParameter)
      .map(_.trim)
      .filter(_.nonEmpty)
      .mkString(" ")

  private def createDirectory(name: String): Path =
    Files.createDirectories(getTestProjectPath.resolve(name))

  private def normalize(path: Path): String =
    path.toAbsolutePath.normalize.toString.replace('\\', '/')
}
