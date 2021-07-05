package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ComparisonTestBase.{outPath, sourcePath}
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.{Files, Path, Paths}
import scala.jdk.StreamConverters._

abstract class ComparisonTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0
  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(classOf[ComparisonTestBase])

  def doTest(testName: String, succeeds: Boolean): Unit

  protected def setupFiles(testName: String): Seq[PsiFile] = {
    val testDirPath = sourcePath.resolve(testName)
    val testFilePath = sourcePath.resolve(testName + ".scala")
    val (source, sourceBasePath) =
      if (Files.isDirectory(testDirPath)) {
        (testDirPath, testDirPath)
      } else {
        assert(Files.isRegularFile(testFilePath))
        (testFilePath, sourcePath)
      }

    for (filePath <- allPathsIn(source).toSeq) yield {
      myFixture.addFileToProject(
        sourceBasePath.relativize(filePath).toString,
        FileUtil.loadFile(filePath.toFile)
      )
    }
  }

  private def allPathsIn(path: Path): Iterator[Path] = {
    if (Files.isRegularFile(path)) Iterator(path)
    else if (Files.isDirectory(path))
      Files.list(path).toScala(Seq).sorted.iterator
    else Iterator.empty
  }
}

object ComparisonTestBase {
  def testPathBase: Path = Paths.get(TestUtils.getTestDataPath, "lang", "resolveSemanticDb")
  def sourcePath: Path = testPathBase.resolve("source")
  def outPath: Path = testPathBase.resolve("out")
}