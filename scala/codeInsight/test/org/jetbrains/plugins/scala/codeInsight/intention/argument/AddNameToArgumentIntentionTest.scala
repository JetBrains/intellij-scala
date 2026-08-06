package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

class AddNameToArgumentIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.use.named.arguments")

  def test1(): Unit = {
    val text =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(t${CARET}rue)
         |}
      """.stripMargin
    val resultText =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(flag = t${CARET}rue)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test2(): Unit = {
    val text =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean, a: Int) {}
         |
         |  doSomething(t${CARET}rue, 8)
         |}
      """.stripMargin
    val resultText =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean, a: Int) {}
         |
         |  doSomething(flag = t${CARET}rue, a = 8)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test3(): Unit = {
    val text =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean, a: Int, b: Int) {}
         |
         |  doSomething(true, 8, ${CARET}9)
         |}
      """.stripMargin
    val resultText =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean, a: Int, b: Int) {}
         |
         |  doSomething(true, 8, ${CARET}b = 9)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testFromJava(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
        |object Test {
        |  val jlist: java.util.List[String] = ???
        |  jlist.add(${CARET}0, "123")
        |}"""
        .stripMargin)
  }

}