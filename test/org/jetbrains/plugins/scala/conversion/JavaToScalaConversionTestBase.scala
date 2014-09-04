package org.jetbrains.plugins.scala
package conversion


import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */
abstract class JavaToScalaConversionTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"

  def folderPath: String = baseRootPath() + "conversion/"

  protected def doTest() {
    import junit.framework.Assert._

    val filePath = folderPath + getTestName(false) + ".java"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".java", fileText)
    val javaFile = getFileAdapter
    val offset = fileText.indexOf(startMarker)
    val startOffset = if (offset != -1) offset + startMarker.length else 0

    val lastPsi = javaFile.findElementAt(javaFile.getText.length - 1)
    var endOffset = fileText.indexOf(endMarker)
    if (endOffset == -1) endOffset = lastPsi.getTextRange.getStartOffset

    var elem: PsiElement = javaFile.findElementAt(startOffset)
    assert(elem.getTextRange.getStartOffset == startOffset)
    while (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] && 
            elem.getParent.getTextRange.getStartOffset == startOffset) {
      elem = elem.getParent
    }
    val buf = new ArrayBuffer[PsiElement]
    buf += elem
    while (elem.getTextRange.getEndOffset < endOffset) {
      elem = elem.getNextSibling
      buf += elem
    }
    var res = JavaToScala.convertPsisToText(buf.toArray)
    val newFile = PsiFileFactory.getInstance(getProjectAdapter).createFileFromText("dummyForJavaToScala.scala",
      ScalaFileType.SCALA_LANGUAGE, res)
    res = CodeStyleManager.getInstance(getProjectAdapter).reformat(newFile).getText

    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case JavaTokenType.END_OF_LINE_COMMENT => text.substring(2).trim
      case JavaTokenType.C_STYLE_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }
    assertEquals(output, res.trim)
  }
}