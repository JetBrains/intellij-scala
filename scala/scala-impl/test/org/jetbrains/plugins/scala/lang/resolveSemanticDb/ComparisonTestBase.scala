package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ComparisonTestBase.{ResolveAffectingOptions, UsingOptionsDirective}
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations.ReferenceComparisonTestConfig
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.fail

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

abstract class ComparisonTestBase(config: ReferenceComparisonTestConfig) extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == config.scalaTargetVersion

  def doTest(testName: String, succeeds: Boolean): Unit

  protected def setupFiles(testName: String): Seq[PsiFile] = {
    def getCaseSensitivePath(name: String): Option[Path] = {
      val path = config.sourcePath.resolve(name)
      Option(path)
        .filter(_.exists)
        .filter(p => realOsPathFileName(p) == name)
    }

    // tests now can contain the file and the directory, and both are considered
    val sources = getCaseSensitivePath(testName).toSeq ++ getCaseSensitivePath(testName + ".scala")

    if (sources.isEmpty) {
      fail(s"Couldn't find any tests for testName '$testName'")
    }

    val files = for (source <- sources; filePath <- allPathsIn(source)) yield {
      (config.sourcePath.relativize(filePath).toString, Files.readString(filePath, StandardCharsets.UTF_8))
    }

    addCompilerOptionsFrom(files.map(_._2))

    for ((path, text) <- files) yield {
      myFixture.addFileToProject(path, text)
    }
  }

  /**
   * Adds the options of `//> using options` directives that affect resolve,
   * as semanticdb is produced by compiling the files with them.
   */
  private def addCompilerOptionsFrom(texts: Seq[String]): Unit = {
    val options = texts
      .flatMap(_.linesIterator)
      .map(_.trim)
      .filter(_.startsWith(UsingOptionsDirective))
      .flatMap(_.stripPrefix(UsingOptionsDirective).trim.split("\\s+"))
      .filter(option => ResolveAffectingOptions.exists(option.startsWith))
      .distinct

    if (options.nonEmpty) {
      val module = getModule
      val configuration = ScalaCompilerConfiguration.instanceIn(getProject)
      val Seq(settings) = configuration.settingsForHighlighting(module)
      val newSettings = settings.copy(additionalCompilerOptions = settings.additionalCompilerOptions ++ options)
      configuration.configureSettingsForModule(module, "unit tests", newSettings)
      // the light project is shared between the tests
      Disposer.register(getTestRootDisposable, () => configuration.configureSettingsForModule(module, "unit tests", settings))
    }
  }

  private def allPathsIn(path: Path): Seq[Path] = {
    if (path.isRegularFile) Seq(path)
    else if (path.isDirectory) path.children().sorted
    else Seq.empty
  }

  private def realOsPathFileName(path: Path): String =
    path.toRealPath().getFileName.toString
}

object ComparisonTestBase {
  private val UsingOptionsDirective = "//> using options"

  private val ResolveAffectingOptions = Seq("-Yimports:", "-Yno-imports", "-Yno-predef", "-Ykind-projector", "-Xkind-projector")
}
