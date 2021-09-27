package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.{PassByValue, ProperArgument, ThisArgument}
import org.junit.Assert.assertTrue

class SimpleMethodCallInfoTest extends InvocationInfoTestBase {

  def testArgumentListInSimpleMethodCall(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |def simpleFun(firstArg: Int, secondArg: Boolean, thirdArg: String, fourthArg: Int): Int = {
         |firstArg + fourthArg
         |}
         |
         |def main(): Int = {
         |${markerStart}simpleFun(3 + 8, 5 > 9, "Hello", 9 * 4 - 2)${markerEnd}
         |}
         |""".stripMargin
    }

    val expectedProperArgsInText = Seq("3 + 8", "5 > 9", "Hello", "9 * 4 - 2")
    val args = invocationInfo.argsInEvaluationOrder

    args.size shouldBe (1 + 4)
    assertTrue("All arguments should be passed by value", args.forall(_.passingMechanism == PassByValue))
    assertTrue("All arguments except the first one should be proper arguments", args.tail.forall(_.kind.is[ProperArgument]))
    args.head.kind shouldBe ThisArgument
    convertArgsToText(args.tail) shouldBe expectedProperArgsInText
  }
}
