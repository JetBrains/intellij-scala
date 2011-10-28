package org.jetbrains.plugins.scala
package lang
package typeInference

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import base.ScalaPsiTestCase
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiManager}
import java.io.File
import java.lang.String
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.api.expr.ScExpression
import psi.api.ScalaFile
import psi.types.result.{TypingContext, Failure, Success}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

abstract class TypeInferenceTestBase extends ScalaPsiTestCase {
  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"

  override def rootPath: String = super.rootPath + "typeInference/"

  protected def doTest = {
    import _root_.junit.framework.Assert._

    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(startExprMarker)
    val startOffset = offset + startExprMarker.length

    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endExprMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val addOne = if(PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset),classOf[ScExpression]) != null) 0 else 1 //for xml tests
    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, classOf[ScExpression])
    assert(expr != null, "Not specified expression in range to infer type.")
    val typez = expr.getType(TypingContext.empty)
    typez match {
      case Success(ttypez, _) => {

        val res = ScType.presentableText(ttypez)
        println("------------------------ " + scalaFile.getName + " ------------------------")
        println("%s (expected types: [%s])".format(res, expr.expectedTypes().toList.map(ScType.presentableText).mkString(",")))
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            text.substring(2, text.length - 2).trim
          case _ => assertTrue("Test result must be in last comment statement.", false)
        }
        val Pattern = """expected: (.*)""".r
        output match {
          case "expected: <none>" =>
            expr.expectedType() match {
              case Some(et) => fail("found unexpected expected type: %s".format(ScType.presentableText(et)))
              case None => // all good
            }
          case Pattern(expectedExpectedTypeText) =>
            val actualExpectedType = expr.expectedType().getOrElse(Predef.error("no expected type"))
            val actualExpectedTypeText = ScType.presentableText(actualExpectedType)
            assertEquals(expectedExpectedTypeText, actualExpectedTypeText)
          case _ => assertEquals(output, res)
        }
      }
      case Failure(msg, elem) => assert(false, msg + " :: " + (elem match {case Some(x) => x.getText case None => "empty element"}))
    }
  }
}