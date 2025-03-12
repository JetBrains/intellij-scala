package org.jetbrains.plugins.scala.lang.optimize.generated

import com.intellij.application.options.CodeStyle
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase
import org.jetbrains.plugins.scala.util.RevertableChange
import org.scalafmt.dynamic.ScalafmtVersion

import java.nio.file.Path

abstract class OptimizeImportsWithScalafmtFormatterBase
  extends OptimizeImportsTestBase
    with ScalaFmtForTestsSetupOps {

  protected def scalafmtVersion: String

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def folderPath: Path = super.folderPath / "scalafmt"

  protected override def sourceRootPath: Path = folderPath

  override protected def scalafmtConfigsBasePath: Path = folderPath

  protected lazy val scalaCodeStyleSettings = CodeStyle.getSettings(getProject).getCustomSettings(classOf[ScalaCodeStyleSettings])

  override def setUp(): Unit = {
    super.setUp()

    ScalaFmtForTestsSetupOps.ensureDownloaded(
      ScalafmtVersion.parse(scalafmtVersion).get,
    )

    val scalafmtConfigFileName = getTestName(false) + ".scalafmt.conf"
    setScalafmtConfig(scalafmtConfigFileName)

    scalaCodeStyleSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
  }
}

final class OptimizeImportsWithScalafmtFormatter extends OptimizeImportsWithScalafmtFormatterBase {
  override protected def scalafmtVersion: String = "3.7.15"

  def testWithMaxColumn(): Unit = {
    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = false
    doTest()

    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = true
    doTest()
  }

  def testSCL21765(): Unit = {
    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = false
    doTest()

    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = true
    doTest()
  }

  def testSCL21765_LocalImports(): Unit = {
    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = false
    doTest()

    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = true
    doTest()
  }
}

final class OptimizeImportsWithScalafmtFormatterWithLibraries extends OptimizeImportsWithScalafmtFormatterBase {
  override protected def scalafmtVersion: String = "3.8.1"

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader("dev.zio" %% "zio" % "2.1.12"),
    IvyManagedLoader("com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"),
  )

  def testSCL23213(): Unit = RevertableChange.withCompilerSettingsModified(
    getModule,
    s => s.copy(additionalCompilerOptions = s.additionalCompilerOptions :+ "-Xsource:3-cross")
  ) {
    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = false
    doTest()

    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = true
    doTest()
  }
}

class OptimizeImportsWithScalafmtFormatterRenamedImportsSource3 extends OptimizeImportsWithScalafmtFormatterBase {
  override protected def scalafmtVersion: String = "3.8.3"

  def testSCL23689_NoChange(): Unit = runSCL23689Test()

  def testSCL23689_Optimize(): Unit = runSCL23689Test()

  private def runSCL23689Test(): Unit = RevertableChange.withCompilerSettingsModified(
    getModule,
    s => s.copy(additionalCompilerOptions = s.additionalCompilerOptions :+ "-Xsource:3")
  ) {
    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = false
    doTest()

    scalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = true
    doTest()
  }
}
