package org.jetbrains.plugins.scala.codeInspection.implicits

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

class UnresolvedImplicitParametersInspectionTest extends ImplicitParameterResolutionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnresolvedImplicitParametersInspection]

  private def description(types: String*): String =
    types.mkString("Implicit parameters not found for the following types: ", ", ", "")

  def testCorrectImplicits(): Unit = {
    val scalaCode =
      """
        |object A {
        |  implicit val implicitInt = 42
        |  val v1: Int = implicitly
        |  val v2 = implicitly[Int]
        |}
      """.stripMargin
    checkTextHasNoErrors(scalaCode, description("Int"))
  }

  def testUnresolvedImplicits(): Unit = {
    val scalaCode =
      s"""
         |object A {
         |  val v = ${START}implicitly[Int]$END
         |}
      """.stripMargin
    checkTextHasError(scalaCode, description("Int"))
  }

  def testPair(): Unit = {
    val scalaCode =
      s"""
         |object A {
         |  def foo(implicit i: Int, d: Double) = (i, d)
         |  ${START}foo$END
         |}
       """.stripMargin
    checkTextHasError(scalaCode, description("Int", "Double"))
  }
}
