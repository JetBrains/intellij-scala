package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.convertNullInitializerToUnderscore.ConvertNullInitializerToUnderscore

/**
  * Created by a.tsukanov on 27.05.2016.
  */
class ConvertNullInitializerToUnderscoreInspectionTest extends ScalaLightInspectionFixtureTestAdapter{
  override protected def annotation: String = ConvertNullInitializerToUnderscore.inspectionName
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ConvertNullInitializerToUnderscore]

  def testSimpleCase(): Unit = {
    def testType(typeName: String): Unit = {
      val declaration = s"${START}var x: $typeName = null$END"

      check(declaration)
      testFix(declaration, s"var x: $typeName = _", annotation)
    }

    testType("String")
    testType("Unit")
    testType("List[_]")
  }

  def testDeclarationsWithStdValType(): Unit = {
    def testType(typeName: String): Unit = checkTextHasNoErrors(s"var x: $typeName = null")

    testType("Char")
    testType("Boolean")
    testType("Byte")
    testType("Short")
    testType("Int")
    testType("Long")
  }

  def testMultiDeclaration(): Unit = {
    val declaration = s"${START}var a, b, c: String = null$END"

    check(declaration)
    testFix(declaration, "var a, b, c: String = _", annotation)
  }

  def testDeclarationWithUnderscore(): Unit = {
    checkTextHasNoErrors("var x: Unit = _")
    checkTextHasNoErrors("var x: String = _")
    checkTextHasNoErrors("var a, b, c: String = _")
  }

  def testInvalidCode(): Unit = {
    checkTextHasNoErrors("var x: String = nulll")
    checkTextHasNoErrors("var x: String = 0")
  }

  def testValDeclaration(): Unit = {
    checkTextHasNoErrors("val x: String = null")
    checkTextHasNoErrors("val x: Unit = null")
  }
}
