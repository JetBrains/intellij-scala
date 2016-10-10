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

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
abstract class AnnotatorTestBase extends PlatformTestCase {

  def testdataPath: String = TestUtils.getTestDataPath + "/annotator/Sbt/"

  def loadTestFile(): SbtFileImpl = {
    val fileName = getTestName(false) + ".sbt"
    val filePath = testdataPath + fileName
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    PsiFileFactory.getInstance(getProject).createFileFromText(fileName , SbtFileType, fileText).asInstanceOf[SbtFileImpl]
  }
}
