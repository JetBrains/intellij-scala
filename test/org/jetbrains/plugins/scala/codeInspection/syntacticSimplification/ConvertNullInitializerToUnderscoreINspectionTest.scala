package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.ConvertNullInitializerToUnderscore

/**
  * Created by a.tsukanov on 27.05.2016.
  */
class ConvertNullInitializerToUnderscoreInspectionTest extends ScalaLightInspectionFixtureTestAdapter{
  override protected def annotation: String = ConvertNullInitializerToUnderscore.inspectionName
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ConvertNullInitializerToUnderscore]

  def testSimpleCase(): Unit = {
    def testType(typeName: String): Unit = {
      val declaration =
        s"""class A {
           |  var x: $typeName = ${START}null$END
           |}""".stripMargin

      val fixedDeclaration =
        s"""class A {
            |  var x: $typeName = _
            |}""".stripMargin

      check(declaration)
      testFix(declaration, fixedDeclaration, annotation)
    }

    testType("String")
    testType("Unit")
    testType("List[_]")
  }

  def testDeclarationsWithStdValueType(): Unit = {
    def testType(typeName: String): Unit = checkTextHasNoErrors(s"var x: $typeName = null")

    testType("Char")
    testType("Boolean")
    testType("Byte")
    testType("Short")
    testType("Int")
    testType("Long")
  }

  def testMultiDeclaration(): Unit = {
    val declaration =
      s"""class A {
         |  var a, b, c: String = ${START}null$END
         |}""".stripMargin

    val fixedDeclaration =
      s"""class A {
          |  var a, b, c: String = _
          |}""".stripMargin

    check(declaration)
    testFix(declaration, fixedDeclaration, annotation)
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

  def testLocalDeclaration(): Unit = {
    checkTextHasNoErrors("val x: String = null")
    checkTextHasNoErrors("val x: Unit = null")
  }
}
