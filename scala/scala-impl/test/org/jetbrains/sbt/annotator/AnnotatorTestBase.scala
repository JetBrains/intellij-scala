package org.jetbrains.sbt
package annotator

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.PlatformTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.language.{SbtFileImpl, SbtFileType}
import org.junit.Assert._

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
abstract class AnnotatorTestBase extends PlatformTestCase {
  protected def testdataPath: String = s"${TestUtils.getTestDataPath}/annotator/Sbt/"

  def loadTestFile(): SbtFileImpl = {
    val filePath = s"$testdataPath/${getTestName(false)}.sbt"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull(filePath, file)
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    assertFalse(fileText.isEmpty)
    PsiFileFactory.getInstance(getProject).createFileFromText(s"${getTestName(false)}.sbt", SbtFileType, fileText).asInstanceOf[SbtFileImpl]
  }
}
