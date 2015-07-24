package org.jetbrains.sbt
package annotator

import java.io.File

import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiFileFactory
import org.jetbrains.plugins.scala.annotator.AnnotatorHolderMock
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.sbt.language.{SbtFileImpl, SbtFileType}

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
abstract class AnnotatorTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def folderPath  = baseRootPath() + "/annotator/Sbt/"

  def testFileExt = ".sbt"

  def loadTestFile() = {
    val fileName = getTestName(false) + testFileExt
    val filePath = folderPath + fileName
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    PsiFileFactory.getInstance(getProjectAdapter).createFileFromText(fileName , SbtFileType, fileText).asInstanceOf[SbtFileImpl]
  }
}
