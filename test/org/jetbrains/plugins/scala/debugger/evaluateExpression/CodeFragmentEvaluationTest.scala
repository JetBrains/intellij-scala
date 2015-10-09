package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12_M2}

/**
 * @author Nikolay.Tropin
 */

class CodeFragmentEvaluationTest extends CodeFragmentEvaluationTestBase with ScalaVersion_2_11
class CodeFragmentEvaluationTest_2_12_M2 extends CodeFragmentEvaluationTestBase with ScalaVersion_2_12_M2

abstract class CodeFragmentEvaluationTestBase extends ScalaDebuggerTestCase {
  def evaluateCodeFragments(fragmentsWithResults: (String, String)*): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  var n = 0
        |  def main(args: Array[String]) {
        |    val str = "some string"
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      fragmentsWithResults.foreach {
        case (fragment, result) => evalEquals(fragment.stripMargin.trim().replace("\r", ""), result)
      }
    }
  }

  def testCodeFragments(): Unit = {
    evaluateCodeFragments(
      """1 + 1
        |2 + 2
        |3 + 3""" -> "6",

      """n = 0
        |n += 1
        |n = n + 2
        |n""" -> "3",

      """val words = str.split(' ')
        |words(0)
        |""" -> "some",

      """val words = str.split(' ')
        |words(1)
        |""" -> "string",

      """val str = "other string"
        |val words = str.split(' ')
        |words(0)
        |""" -> "other",

      """val Seq(first, second) = str.split(' ').toSeq
        |(first, second)
        |""" -> "(some,string)",

      """val words, Seq(first, second) = str.split(' ').toSeq
        |words(0)
        |""" -> "some",

      """var i = 0
        |i += 25
        |i""" -> "25",

      """var res = 0
        |val z = 1
        |if (true) {
        |  val z = 2
        |  res = z
        |}
        |res""" -> "2",

      """var res = 0
        |val z = 1
        |if (true) {
        |  val z = 2
        |  res = z
        |}
        |res = z
        |res""" -> "1",

      """n = 0; val x = n + 1; x""" -> "1",

      """var sum = 0
        |var i = 0
        |while(i <= 5) {
        |  sum += i
        |  i += 1
        |}
        |sum""" -> "15"

    )
  }

}
