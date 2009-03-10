package org.jetbrains.plugins.scala.lang.typeInference

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import base.ScalaPsiTestCase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import psi.api.expr.ScExpression
import psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

class TypeInferenceTest extends ScalaPsiTestCase {
  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"

  /*//use it if you want to generate tests from appropriate folder
  def testGenerate {
    generateTests
  }*/

  def testIsInstanceOf {
    testPath = "/typeInference/genericCall/IsInstanceOf"
    realOutput = """
() => Boolean
"""
    realOutput = realOutput.trim
    playTest
  }

  protected def getTestOutput(file: VirtualFile, useOutput: Boolean): String = {
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(startExprMarker)
    val startOffset = offset + startExprMarker.length
    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endExprMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")
    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset, endOffset, classOf[ScExpression])
    assert(expr != null, "Not specified expression in range to infer type.")
    val typez = expr.getType
    val res = ScType.presentableText(typez)
    if (useOutput) {
      println("------------------------ " + scalaFile.getName + " ------------------------")
      println(res)
    }
    res
  }

  private def generateTests {
    generateTests("typeInference")
  }
}