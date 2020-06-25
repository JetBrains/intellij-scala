package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.VariableNullInitializerInspection

/**
  * Created by a.tsukanov on 27.05.2016.
  */
class VariableNullInitializerInspectionTest extends ScalaQuickFixTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[VariableNullInitializerInspection]

  override protected val description: String = VariableNullInitializerInspection.inspectionName

  def testSimpleCase(): Unit = {
    def testType(typeName: String): Unit = {
      val declaration =
        s"""
           |object Moo {
           |  var x: $typeName = ${START}null$END
           |}
         """.stripMargin

      checkTextHasError(declaration)
      val result =
        s"""
          |object Moo {
          |  var x: $typeName = _
          |}
        """.stripMargin
      testQuickFix(declaration, result, description)
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

    checkTextHasError(declaration)
    testQuickFix(declaration,
      s"""
         |object Moo {
         |  var a, b, c: String = _
         |}
       """.stripMargin, description)
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
    checkTextHasError(code)
    testQuickFix(code, wrapInObject(s"private var x: String = _"), description)
  }

  private def wrapInObject(code: String): String =
    s"""
      |object Moo {
      |  $code
      |}
    """.stripMargin

}