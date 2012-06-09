package org.jetbrains.plugins.scala
package codeInsight.intentions.argument

import codeInsight.intentions.ScalaIntentionTestBase
import codeInsight.intention.argument.AddNameToArgumentIntention

/**
 * @author Ksenia.Sautina
 * @since 5/14/12
 */

class AddNameToArgumentIntentionTest extends ScalaIntentionTestBase {
  def familyName = AddNameToArgumentIntention.familyName

  def test() {
    val text = """
                 |class NameParameters {
                 |  def doSomething(flag: Boolean) {}
                 |
                 |  doSomething(t<caret>rue)
                 |}
               """.stripMargin.replace("\r", "").trim
    val resultText = """
                       |class NameParameters {
                       |  def doSomething(flag: Boolean) {}
                       |
                       |  doSomething(flag = t<caret>rue)
                       |}
                     """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }

  def test2() {
    val text = """
                 |class NameParameters {
                 |  def doSomething(flag: Boolean, a: Int) {}
                 |
                 |  doSomething(t<caret>rue, 8)
                 |}
               """.stripMargin.replace("\r", "").trim
    val resultText = """
                       |class NameParameters {
                       |  def doSomething(flag: Boolean, a: Int) {}
                       |
                       |  doSomething(flag = t<caret>rue, a = 8)
                       |}
                     """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }

  def test3() {
    val text = """
                 |class NameParameters {
                 |  def doSomething(flag: Boolean, a: Int, b: Int) {}
                 |
                 |  doSomething(true, 8, <caret>9)
                 |}
               """.stripMargin.replace("\r", "").trim
    val resultText = """
                       |class NameParameters {
                       |  def doSomething(flag: Boolean, a: Int, b: Int) {}
                       |
                       |  doSomething(true, 8, <caret>b = 9)
                       |}
                     """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }

}