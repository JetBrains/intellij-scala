package org.jetbrains.plugins.scala
package codeInsight
package daemon

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class ScalaRainbowVisitorTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaRainbowVisitorTest.{END_TAG => E, START_TAG => S, START_TAG_1 => S_1, START_TAG_2 => S_2, START_TAG_3 => S_3, START_TAG_4 => S_4}

  def testRainbowOff(): Unit = doTest(
    s"def foo(p1: Int): Unit = {}",
    isRainbowOn = false
  )

  def testVariables(): Unit = doTest(
    s"""def foo(): Unit = {
       |  var ${S_1}v1$E, ${S_2}v2$E = 42
       |  ${S_1}v1$E = 42
       |
       |  var ${S_3}v3$E = ${S_1}v1$E + ${S_2}v2$E
       |}
       """.stripMargin
  )

  def testValues(): Unit = doTest(
    s"""def foo(): Unit = {
       |  val ${S_1}v1$E, ${S_2}v2$E = 42
       |  val ${S_3}v3$E = ${S_1}v1$E + ${S_2}v2$E
       |}
       """.stripMargin
  )

  def testProperties(): Unit = doTest(
    s"""class Foo {
       |  val foo: Int = 42
       |  var bar: Int = 42
       |}
     """.stripMargin
  )

  def testParameters(): Unit = doTest(
    s"""def foo(${S_1}p1$E: Int, ${S_2}p2$E: Int): Unit = {
       |  val ${S_3}v3$E = ${S_1}p1$E + ${S_2}p2$E
       |}
       """.stripMargin
  )

  def testClassParameters(): Unit = doTest(
    s"""class Foo(p1: Int) {
       |  def foo(${S}p2$E: Int): Unit = {}
       |}
       """.stripMargin,
    withColor = false
  )

  def testLambdaParameters(): Unit = doTest(
    s"""def foo(${S_1}p1$E: Any => String = ${S_1}p2$E => ${S_1}p2$E.toString): Unit = {
       |  (${S_4}p3$E: String) => ${S_4}p3$E
       |}
       """.stripMargin
  )

  def testNestedMethods(): Unit = doTest(
    s"""def foo(${S_1}p1$E: Int, ${S_2}p2$E: Int): Unit = {
       |  def bar(${S_1}p1$E: Int): Unit = {
       |    ${S_1}p1$E + ${S_2}p2$E
       |  }
       |}
       """.stripMargin
  )

  private def doTest(text: String,
                     isRainbowOn: Boolean = true,
                     withColor: Boolean = true): Unit =
    getFixture.testRainbow("dummy.scala", text, isRainbowOn, withColor)
}

object ScalaRainbowVisitorTest {

  private val START_TAG = "<rainbow>"
  private val START_TAG_1 = "<rainbow color='ff000001'>"
  private val START_TAG_2 = "<rainbow color='ff000002'>"
  private val START_TAG_3 = "<rainbow color='ff000003'>"
  private val START_TAG_4 = "<rainbow color='ff000004'>"

  private val END_TAG = "</rainbow>"
}
