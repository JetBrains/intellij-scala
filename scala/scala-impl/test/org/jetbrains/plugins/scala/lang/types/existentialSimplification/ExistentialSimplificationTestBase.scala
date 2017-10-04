package org.jetbrains.plugins.scala
package lang.types.existentialSimplification

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialType, ScTypeExt}

/**
 * @author Alexander Podkhalyuzin
 */
abstract class ExistentialSimplificationTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"

  def folderPath: String = baseRootPath() + "types/existentialSimplification/"

  protected def doTest() {
    import _root_.junit.framework.Assert._

    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
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
      case Success(ttypez: ScExistentialType, _) =>

        val res = ttypez.simplify().presentableText
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            text.substring(2, text.length - 2).trim
          case _ => assertTrue("Test result must be in last comment statement.", false)
        }
        assertEquals(output, res)
      case Success(_, _) =>
        assert(assertion = false, message = "Expression has not existential type")
      case Failure(msg, elem) => assert(assertion = false, message = msg + " :: " + (elem match {
        case Some(x) => x.getText
        case None => "empty element"
      }))
    }
  }
}