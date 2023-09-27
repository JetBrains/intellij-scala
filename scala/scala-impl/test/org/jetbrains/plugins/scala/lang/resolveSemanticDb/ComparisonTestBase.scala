package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ComparisonTestBase.sourcePath
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.assertTrue

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.StreamConverters._

abstract class ComparisonTestBase extends ScalaLightCodeInsightFixtureTestCase {

  def doTest(testName: String, succeeds: Boolean): Unit

  protected def setupFiles(testName: String): Seq[PsiFile] = {
    val testDirPath = sourcePath.resolve(testName)
    val testFilePath = sourcePath.resolve(testName + ".scala")
    val (source, sourceBasePath) =
      if (Files.isDirectory(testDirPath)) {
        (testDirPath, testDirPath)
      } else {
        assertTrue(s"Test file does not exist: $testFilePath", Files.exists(testFilePath))
        assertTrue(s"Test file is not a regular file: $testFilePath", Files.isRegularFile(testFilePath))
        (testFilePath, sourcePath)
      }

    for (filePath <- allPathsIn(source).toSeq) yield {
      myFixture.addFileToProject(
        sourceBasePath.relativize(filePath).toString,
        FileUtil.loadFile(filePath.toFile, StandardCharsets.UTF_8)
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