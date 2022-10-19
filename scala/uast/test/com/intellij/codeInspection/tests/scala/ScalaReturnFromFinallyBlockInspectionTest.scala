package com.intellij.codeInspection.tests.scala

import com.siyeh.ig.errorhandling.ReturnFromFinallyBlockInspection
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class ScalaReturnFromFinallyBlockInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[ReturnFromFinallyBlockInspection]
  override protected val description = "'return' inside 'finally' block"

  def testHighlighting(): Unit = checkTextHasError(
    s"""
       |object ReturnFromFinally {
       |  def foo: String = try println("foo") finally ${START}return "bar"$END
       |
       |  def bar: Int = try {
       |    println()
       |  } catch {
       |    case _: Exception =>
       |  } finally {
       |    ${START}return 2$END
       |  }
       |}
       |""".stripMargin
  )
}
