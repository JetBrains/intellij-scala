package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations.ReferenceComparisonTestConfig
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

    for (source <- sources; filePath <- allPathsIn(source)) yield {
      myFixture.addFileToProject(
        config.sourcePath.relativize(filePath).toString,
        Files.readString(filePath, StandardCharsets.UTF_8)
      )
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
