package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.VariableNullInitializerInspection

/**
  * Created by a.tsukanov on 27.05.2016.
  */
class VariableNullInitializerInspectionTest extends ScalaLightInspectionFixtureTestAdapter {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected def annotation: String = VariableNullInitializerInspection.inspectionName

  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[VariableNullInitializerInspection]

  def testSimpleCase(): Unit = {
    def testType(typeName: String): Unit = {
      val declaration =
        s"""
           |object Moo {
           |  var x: $typeName = ${START}null$END
           |}
         """.stripMargin

      check(declaration)
      val result =
        s"""
          |object Moo {
          |  var x: $typeName = _
          |}
        """.stripMargin
      testFix(declaration, result, annotation)
    }
    testType("String")
    testType("Unit")
    testType("List[_]")
  }

  def testDeclarationsWithStdValType(): Unit = {
    def testType(typeName: String): Unit = checkTextHasNoErrors(
      s"""
        |object Moo {
        |  var x: $typeName = null
        |}
      """.stripMargin)

    testType("Char")
    testType("Boolean")
    testType("Byte")
    testType("Short")
    testType("Int")
    testType("Long")
  }

  def testMultiDeclaration(): Unit = {
    val declaration =
      s"""
         |object Moo {
         |  var a, b, c: String = ${START}null$END
         |}
       """.stripMargin

    check(declaration)
    testFix(declaration,
      s"""
         |object Moo {
         |  var a, b, c: String = _
         |}
       """.stripMargin, annotation)
  }

  def testDeclarationWithUnderscore(): Unit = {
    checkTextHasNoErrors(wrapInObject("var x: Unit = _"))
    checkTextHasNoErrors(wrapInObject("var x: String = _"))
    checkTextHasNoErrors(wrapInObject("var a, b, c: String = _"))
  }

  def testInvalidCode(): Unit = {
    checkTextHasNoErrors(wrapInObject("var x: String = nulll"))
    checkTextHasNoErrors(wrapInObject("var x: String = 0"))
  }

  def testValDeclaration(): Unit = {
    checkTextHasNoErrors(wrapInObject("val x: String = null"))
    checkTextHasNoErrors(wrapInObject("val x: Unit = null"))
  }

  def testDoesNotRemoveModifiers(): Unit = {
    val code = wrapInObject(s"private var x: String = ${START}null$END")
    check(code)
    testFix(code, wrapInObject(s"private var x: String = _"), annotation)
  }

  private def wrapInObject(code: String): String =
    s"""
      |object Moo {
      |  $code
      |}
    """.stripMargin

}