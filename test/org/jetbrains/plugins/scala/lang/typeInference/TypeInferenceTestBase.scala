package org.jetbrains.plugins.scala
package lang
package typeInference

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Unit}
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

abstract class TypeInferenceTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"
  private val fewVariantsMarker = "Few variants:"

  protected def folderPath: String = TestUtils.getTestDataPath + "/typeInference/"

  protected def doTest() {
    import org.junit.Assert._

    val filePath = folderPath + getTestName(false) + ".scala"
    val ioFile: File = new File(filePath)
    var fileText: String = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    configureFromFileTextAdapter(ioFile.getName, fileText)
    val scalaFile: ScalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(startExprMarker)
    val startOffset = offset + startExprMarker.length

    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endExprMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val addOne = if(PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset),classOf[ScExpression]) != null) 0 else 1 //for xml tests
    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, classOf[ScExpression])
    assert(expr != null, "Not specified expression in range to infer type.")
    val typez = expr.getType(TypingContext.empty) match {
      case Success(Unit, _) => expr.getTypeIgnoreBaseType(TypingContext.empty)
      case x => x
    }
    typez match {
      case Success(ttypez, _) =>
        val res = ScType.presentableText(ttypez)
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            val resText = text.substring(2, text.length - 2).trim
            if (resText.startsWith(fewVariantsMarker)) {
              val results = resText.substring(fewVariantsMarker.length).trim.split('\n')
              if (!results.contains(res)) assertEquals(results(0), res)
              return
            } else resText
          case _ =>
            throw new AssertionError("Test result must be in last comment statement.")
        }
        val Pattern = """expected: (.*)""".r
        output match {
          case "expected: <none>" =>
            expr.expectedType() match {
              case Some(et) => fail("found unexpected expected type: %s".format(ScType.presentableText(et)))
              case None => // all good
            }
          case Pattern(expectedExpectedTypeText) =>
            val actualExpectedType = expr.expectedType().getOrElse(sys.error("no expected type"))
            val actualExpectedTypeText = ScType.presentableText(actualExpectedType)
            assertEquals(expectedExpectedTypeText, actualExpectedTypeText)
          case _ => assertEquals(output, res)
        }
      case Failure(msg, elem) => assert(assertion = false, msg + " :: " + (elem match {case Some(x) => x.getText case None => "empty element"}))
    }
  }
}