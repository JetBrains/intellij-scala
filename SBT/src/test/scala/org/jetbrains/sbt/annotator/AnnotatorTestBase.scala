package org.jetbrains.sbt
package annotator

import java.io.File

import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiFileFactory
import org.jetbrains.plugins.scala.annotator.{Message, AnnotatorHolderMock}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import org.jetbrains.sbt.language.SbtFileType

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
abstract class AnnotatorTestBase[T <: Annotator](val cls: Class[T]) extends ScalaLightPlatformCodeInsightTestCaseAdapter{

  import _root_.junit.framework.Assert._

  def folderPath  = baseRootPath() + "/annotator/Sbt/"
  def testFileExt = ".sbt"

  def loadFile() = {
    val fileName = getTestName(false) + testFileExt
    val filePath = folderPath + fileName
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    PsiFileFactory.getInstance(getProjectAdapter).createFileFromText(fileName , SbtFileType, fileText).asInstanceOf[ScalaFile]
  }

  def doTest(messages: Seq[Message]) {
    val element = loadFile()
    val annotator = cls.newInstance()
    val mock = new AnnotatorHolderMock

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitLiteral(lit: ScLiteral) {
        annotator.annotate(lit, mock);
        super.visitLiteral(lit)
      }
    }
    element.accept(visitor)
    assertEquals(messages, mock.annotations)
  }
}
