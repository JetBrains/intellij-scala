package org.jetbrains.plugins.scala
package debugger
package evaluateExpression

import org.junit.experimental.categories.Category

/**
 * @author Nikolay.Tropin
 */
@Category(Array(classOf[DebuggerTests]))
class CodeFragmentEvaluationTest extends CodeFragmentEvaluationTestBase {
  override implicit val version: ScalaVersion = Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class CodeFragmentEvaluationTest_212 extends CodeFragmentEvaluationTestBase {
  override implicit val version: ScalaVersion = Scala_2_12
}

abstract class CodeFragmentEvaluationTestBase extends ScalaDebuggerTestCase {

  addFileWithBreakpoints ( "CodeFragments.scala",
    s"""
       |object CodeFragments {
       |  var n = 0
       |  def main(args: Array[String]) {
       |    val str = "some string"
       |    ""$bp
       |  }
       |}
      """.stripMargin.trim()
  )

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
