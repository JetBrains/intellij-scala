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

  //use it if you want to generate tests from appropriate folder
  def testGenerate {
    generateTests
  }

  //--------------------------------------- Generic Call ---------------------------------------------------
  def testIsInstanceOf {
    testPath = "/typeInference/genericCall/IsInstanceOf"
    realOutput = """
() => Boolean
"""
    realOutput = realOutput.trim
    playTest
  }

  def testJavaGenericFunction {
    testPath = "/typeInference/genericCall/JavaGenericFunction"
    realOutput = """
ArrayList[Int]
"""
    realOutput = realOutput.trim
    playTest
  }

  def testAsInstanceOf {
    testPath = "/typeInference/genericCall/AsInstanceOf"
    realOutput = """
() => Float
"""
    realOutput = realOutput.trim
    playTest
  }

  def testGenericFunction {
    testPath = "/typeInference/genericCall/GenericFunction"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testCaseClasses {
    testPath = "/typeInference/genericCall/CaseClasses"
    realOutput = """
CaseClasses[Int]
"""
    realOutput = realOutput.trim
    playTest
  }

  def testObjectGenericApply {
    testPath = "/typeInference/genericCall/ObjectGenericApply"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testInstanceGenericApply {
    testPath = "/typeInference/genericCall/InstanceGenericApply"
    realOutput = """
(Int, Double)
"""
    realOutput = realOutput.trim
    playTest
  }

  //-------------------------------------------------- Method Call ----------------------------------------------
  def testApplyCall {
    testPath = "/typeInference/methodCall/ApplyCall"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testCaseClassCall {
    testPath = "/typeInference/methodCall/CaseClassCall"
    realOutput = """
CaseClassCall
"""
    realOutput = realOutput.trim
    playTest
  }

  def testObjectApplyCall {
    testPath = "/typeInference/methodCall/ObjectApplyCall"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testOverloadedCall {
    testPath = "/typeInference/methodCall/OverloadedCall"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testSimpleCall {
    testPath = "/typeInference/methodCall/SimpleCall"
    realOutput = """
Float
"""
    realOutput = realOutput.trim
    playTest
  }

  def testUpdateCall {
    testPath = "/typeInference/methodCall/UpdateCall"
    realOutput = """
A
"""
    realOutput = realOutput.trim
    playTest
  }

  //------------------------------------------- Literals -------------------------------------------------------------

  def testBoolean {
    testPath = "/typeInference/literals/Boolean"
    realOutput = """
Boolean
"""
    realOutput = realOutput.trim
    playTest
  }

  def testChar {
    testPath = "/typeInference/literals/Char"
    realOutput = """
Char
"""
    realOutput = realOutput.trim
    playTest
  }

  def testDouble {
    testPath = "/typeInference/literals/Double"
    realOutput = """
Double
"""
    realOutput = realOutput.trim
    playTest
  }

  def testFloat {
    testPath = "/typeInference/literals/Float"
    realOutput = """
Float
"""
    realOutput = realOutput.trim
    playTest
  }

  def testInt {
    testPath = "/typeInference/literals/Int"
    realOutput = """
Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testLong {
    testPath = "/typeInference/literals/Long"
    realOutput = """
Long
"""
    realOutput = realOutput.trim
    playTest
  }

  def testNull {
    testPath = "/typeInference/literals/Null"
    realOutput = """
Null
"""
    realOutput = realOutput.trim
    playTest
  }

  def testString {
    testPath = "/typeInference/literals/String"
    realOutput = """
String
"""
    realOutput = realOutput.trim
    playTest
  }

  def testSymbol {
    testPath = "/typeInference/literals/Symbol"
    realOutput = """
Symbol
"""
    realOutput = realOutput.trim
    playTest
  }

  //---------------------------------------- Statements -----------------------------------------------

  def testAssignStatement {
    testPath = "/typeInference/statements/AssignStatement"
    realOutput = """
A
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


  override protected def getTestClass = classOf[TypeInferenceTest]
}