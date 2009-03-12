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
    assert(false) //to not forgot to comment this
  }*/

  //--------------------------------------- Generic Call ---------------------------------------------------
  def testIsInstanceOf {
    testPath = "/typeInference/genericCall/IsInstanceOf"
    realOutput = """
() => Boolean
"""
    realOutput = realOutput.trim
    doTest
  }

  def testJavaGenericFunction {
    testPath = "/typeInference/genericCall/JavaGenericFunction"
    realOutput = """
ArrayList[Int]
"""
    realOutput = realOutput.trim
    doTest
  }

  def testAsInstanceOf {
    testPath = "/typeInference/genericCall/AsInstanceOf"
    realOutput = """
() => Float
"""
    realOutput = realOutput.trim
    doTest
  }

  def testGenericFunction {
    testPath = "/typeInference/genericCall/GenericFunction"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    doTest
  }

  def testCaseClasses {
    testPath = "/typeInference/genericCall/CaseClasses"
    realOutput = """
CaseClasses[Int]
"""
    realOutput = realOutput.trim
    doTest
  }

  def testObjectGenericApply {
    testPath = "/typeInference/genericCall/ObjectGenericApply"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    doTest
  }

  def testInstanceGenericApply {
    testPath = "/typeInference/genericCall/InstanceGenericApply"
    realOutput = """
(Int, Double)
"""
    realOutput = realOutput.trim
    doTest
  }

  //-------------------------------------------------- Method Call ----------------------------------------------
  def testApplyCall {
    testPath = "/typeInference/methodCall/ApplyCall"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    doTest
  }

  def testCaseClassCall {
    testPath = "/typeInference/methodCall/CaseClassCall"
    realOutput = """
CaseClassCall
"""
    realOutput = realOutput.trim
    doTest
  }

  def testObjectApplyCall {
    testPath = "/typeInference/methodCall/ObjectApplyCall"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    doTest
  }

  def testOverloadedCall {
    testPath = "/typeInference/methodCall/OverloadedCall"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    doTest
  }

  def testSimpleCall {
    testPath = "/typeInference/methodCall/SimpleCall"
    realOutput = """
Float
"""
    realOutput = realOutput.trim
    doTest
  }

  def testUpdateCall {
    testPath = "/typeInference/methodCall/UpdateCall"
    realOutput = """
UpdateCall
"""
    realOutput = realOutput.trim
    doTest
  }

  //------------------------------------------- Literals -------------------------------------------------------------

  def testBoolean {
    testPath = "/typeInference/literals/Boolean"
    realOutput = """
Boolean
"""
    realOutput = realOutput.trim
    doTest
  }

  def testChar {
    testPath = "/typeInference/literals/Char"
    realOutput = """
Char
"""
    realOutput = realOutput.trim
    doTest
  }

  def testDouble {
    testPath = "/typeInference/literals/Double"
    realOutput = """
Double
"""
    realOutput = realOutput.trim
    doTest
  }

  def testFloat {
    testPath = "/typeInference/literals/Float"
    realOutput = """
Float
"""
    realOutput = realOutput.trim
    doTest
  }

  def testInt {
    testPath = "/typeInference/literals/Int"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    doTest
  }

  def testLong {
    testPath = "/typeInference/literals/Long"
    realOutput = """
Long
"""
    realOutput = realOutput.trim
    doTest
  }

  def testNull {
    testPath = "/typeInference/literals/Null"
    realOutput = """
Null
"""
    realOutput = realOutput.trim
    doTest
  }

  def testString {
    testPath = "/typeInference/literals/String"
    realOutput = """
String
"""
    realOutput = realOutput.trim
    doTest
  }

  def testSymbol {
    testPath = "/typeInference/literals/Symbol"
    realOutput = """
Symbol
"""
    realOutput = realOutput.trim
    doTest
  }

  //---------------------------------------- Statements -----------------------------------------------

  def testAssignStatement {
    testPath = "/typeInference/statements/AssignStatement"
    realOutput = """
AssignStatement
"""
    realOutput = realOutput.trim
    doTest
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


  override protected def getTestClass = classOf[TypeInferenceTest]
}