package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api.{ScTypePresentation, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success}
import org.junit.Assert._

/**
  * Created by Svyatoslav Ilinskiy on 01.07.16.
  */
trait TypeInferenceDoTest {
  protected val START = "/*start*/"
  protected val END = "/*end*/"
  private val fewVariantsMarker = "Few variants:"
  private val ExpectedPattern = """expected: (.*)""".r
  private val SimplifiedPattern = """simplified: (.*)""".r

  def configureFromFileText(fileName: String, fileText: Option[String]): ScalaFile

  protected def doTest(fileText: String): Unit = doTest(Some(fileText))

  protected def doTest(fileText: Option[String], fileName: String = "dummy.scala"): Unit = {
    val scalaFile: ScalaFile = configureFromFileText(fileName, fileText)
    val expr: ScExpression = findExpression(scalaFile)
    val typez = expr.getType() match {
      case Success(Unit, _) => expr.getTypeIgnoreBaseType
      case x => x
    }
    typez match {
      case Success(ttypez, _) =>
        val res = ttypez.presentableText
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
        output match {
          case ExpectedPattern("<none>") =>
            expr.expectedType() match {
              case Some(et) => fail("found unexpected expected type: %s".format(et.presentableText))
              case None => // all good
            }
          case ExpectedPattern(expectedExpectedTypeText) =>
            val actualExpectedType = expr.expectedType().getOrElse(sys.error("no expected type"))
            val actualExpectedTypeText = actualExpectedType.presentableText
            assertEquals(expectedExpectedTypeText, actualExpectedTypeText)
          case SimplifiedPattern(expectedText) =>
            assertEquals(expectedText, ScTypePresentation.withoutAliases(ttypez))
          case _ => assertEquals(output, res)
        }
      case Failure(msg, elem) => assert(assertion = false, msg + " :: " + (elem match {case Some(x) => x.getText case None => "empty element"}))
    }
  }

  def findExpression(scalaFile: ScalaFile): ScExpression = {
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(START)
    val startOffset = offset + START.length
    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(END)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val addOne = if (PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset), classOf[ScExpression]) != null) 0 else 1 //for xml tests
    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, classOf[ScExpression])
    assert(expr != null, "Not specified expression in range to infer type.")
    expr
  }
}
