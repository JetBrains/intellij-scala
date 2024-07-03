package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.dfa.DfaUnreachableCodeInspection
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

// TODO Inherit from and extend ScalaUnreachableCodeInspectionTest
class DfaUnreachableCodeInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[DfaUnreachableCodeInspection]

  override protected def description: String = ScalaInspectionBundle.message("unreachable.code.name")

  def test_throw(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  throw new Exception()
       |  ${START}println("test")
       |  println("test 2")$END
       |}
       |""".stripMargin
  )

  def test_try_catch(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  try {
       |    throw new Exception()
       |    ${START}println("test")
       |    println("test 2")$END
       |  } catch {
       |   case e =>
       |  }
       |
       |  println("blub")
       |}
       |""".stripMargin
  )

  def test_try_finally(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  try {
       |    throw new Exception()
       |    ${START}println("test")
       |    println("test 2")$END
       |  } finally {
       |    println("blub")
       |  }
       |
       |  ${START}println("blub")$END
       |}
       |""".stripMargin
  )

  def test_while(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  while(true) {
       |  }
       |
       |  ${START}println("blub")$END
       |}
       |""".stripMargin
  )

  def test_condition(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  val v = 1
       |
       |  if (v == 1) {
       |    throw new Exception()
       |  }
       |
       |  ${START}println("blub")$END
       |}
       |""".stripMargin
  )

  def test_throw_in_by_value_arg(): Unit = checkTextHasError(
    s"""
       |def take_by_value(x: Any) = ()
       |
       |def test(): Unit = {
       |  take_by_value {
       |    throw new Exception()
       |    ${START}println("haha")$END
       |  }
       |
       |  ${START}println("blub")$END
       |}
       |""".stripMargin
  )

  def test_error_def(): Unit = checkTextHasNoErrors(
    """
      |def test =
      |""".stripMargin
  )

  def test_unreachable_literal(): Unit = checkTextHasError(
    s"""
       |def fun(): String = {
       |  throw new Exception()
       |  ${START}"I'm unreachable"$END
       |}
       |""".stripMargin
  )
}