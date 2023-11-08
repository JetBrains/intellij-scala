package org.jetbrains.plugins.scala.lang

import org.jetbrains.plugins.scala.lang.dfa.analysis.{DfaManager, ScalaDfaVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

package object dfa {

  object Messages {
    val ConditionAlwaysTrue = "Condition is always true"
    val ConditionAlwaysFalse = "Condition is always false"
    val ExpressionAlwaysZero = "Expression always evaluates to 0"
    val ExpressionAlwaysNull = "Expression always evaluates to null"
  }

  def commonCodeTemplate(returnType: String)(body: String): String =
    s"""
       |import java.util
       |import java.lang.Math
       |import scala.math._
       |
       |class OtherClass {
       |  val otherField: Int = 1244
       |  val yetAnotherField: String = "Hello again"
       |}
       |
       |class Person(val id: Int)
       |case class Student(age: Int, grades: List[Int])
       |
       |class TestClass {
       |  def testMethod(arg1: Int, arg2: Int, arg3: Boolean, arg4: String): $returnType = {
       |    $body
       |  }
       |
       |  def anotherMethod(arg1: Int, arg2: Int, arg3: Boolean, arg4: String): Int = arg2 - arg1
       |
       |  private def verySimpleMethod(): Int = 3 + 2
       |
       |  final def simpleMethodWithArgs(arg1: Int, arg2: Int): Int = {
       |    if (arg1 < arg2) arg2 - arg1 else arg1 - arg2
       |  }
       |
       |  final def methodWithDefaultParam(arg1: Int, arg2: Int, arg3: Int = 7): Int = {
       |    if (arg1 < arg2) arg3 + arg2 - arg1 else arg3 + arg1 - arg2
       |  }
       |
       |  implicit class IntWrapper(wrapped: Int) {
       |    def timesAndPlus(other: Int): Int = wrapped * other + other
       |  }
       |}
       |""".stripMargin

  implicit final class ScalaDfaVisitorExt(private val visitor: ScalaDfaVisitor) extends AnyVal {
    def withBuildingUnsupportedPsiElements: ScalaDfaVisitor = new ScalaDfaVisitor(visitor.resultF) {
      override def visitFunctionDefinition(function: ScFunctionDefinition): Unit =
        DfaManager.computeDfaResultFor(function, buildUnsupportedPsiElements = true).foreach(resultF)
    }
  }
}
