package org.jetbrains.plugins.scala
package conversion


import base.ScalaPsiTestCase
import collection.mutable.ArrayBuffer
import lang.lexer.ScalaTokenTypes
import lang.psi.types.ScType
import lang.psi.api.expr.ScExpression
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import lang.psi.api.ScalaFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi._
import codeStyle.CodeStyleManager

/**
 * Created by IntelliJ IDEA.
 * User: Alexander Podkhalyuzin
 * Date: 23.07.2009
 * Time: 19:18:12
 * To change this template use File | Settings | File Templates.
 */

class JavaToScalaConversionTestBase extends ScalaPsiTestCase {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"

  override def rootPath: String = super.rootPath + "conversion/"

  protected def doTest = {
    import _root_.junit.framework.Assert._

    val filePath = rootPath + getTestName(false) + ".java"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val javaFile: PsiJavaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[PsiJavaFile]
    val fileText = javaFile.getText
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
    var res = JavaToScala.convertPsiToText(buf.toArray)

    println("------------------------ " + javaFile.getName + " ------------------------")
    println(res)

    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case JavaTokenType.END_OF_LINE_COMMENT => text.substring(2).trim
      case JavaTokenType.DOC_COMMENT | JavaTokenType.C_STYLE_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }
    assertEquals(output, res)
  }
}