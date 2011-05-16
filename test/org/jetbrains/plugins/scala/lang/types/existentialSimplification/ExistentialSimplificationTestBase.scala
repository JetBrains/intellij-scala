package org.jetbrains.plugins.scala.lang.types.existentialSimplification

import org.jetbrains.plugins.scala.base.ScalaPsiTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialType, ScType}

/**
 * @author Alexander Podkhalyuzin
 */

abstract class ExistentialSimplificationTestBase extends ScalaPsiTestCase {
  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"

  override def rootPath: String = super.rootPath + "types/existentialSimplification/"

  protected def doTest {
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
      case Success(ttypez: ScExistentialType, _) => {

        val res = ScType.presentableText(ttypez.simplify())
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            text.substring(2, text.length - 2).trim
          case _ => assertTrue("Test result must be in last comment statement.", false)
        }
        assertEquals(output, res)
      }
      case Success(_, _) =>
        assert(false, "Expression has not existential type")
      case Failure(msg, elem) => assert(false, msg + " :: " + (elem match {case Some(x) => x.getText case None => "empty element"}))
    }
  }
}