package org.jetbrains.plugins.scala
package lang
package typeInference

import java.io.File

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.Unit
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

abstract class TypeInferenceTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected val START = "/*start*/"
  protected val END = "/*end*/"
  private val fewVariantsMarker = "Few variants:"
  private val ExpectedPattern = """expected: (.*)""".r
  private val SimplifiedPattern = """simplified: (.*)""".r

  protected def folderPath: String = TestUtils.getTestDataPath + "/typeInference/"

  protected def doInjectorTest(injector: SyntheticMembersInjector): Unit = {
    val extensionPoint = Extensions.getRootArea.getExtensionPoint(SyntheticMembersInjector.EP_NAME)
    extensionPoint.registerExtension(injector)
    try {
      doTest()
    } finally {
      extensionPoint.unregisterExtension(injector)
    }
  }

  protected def doTest(fileText: String, fileName: String = "dummy.scala") {
    val cleanedText = fileText.replace("\r", "")
    configureFromFileTextAdapter(fileName, cleanedText)
    val scalaFile: ScalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val offset = cleanedText.indexOf(START)
    val startOffset = offset + START.length

    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = cleanedText.indexOf(END)
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

  protected def doTest() {
    val filePath = folderPath + getTestName(false) + ".scala"
    val ioFile: File = new File(filePath)
    var fileText: String = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    doTest(fileText, ioFile.getName)
  }
}