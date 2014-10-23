package org.jetbrains.plugins.scala
package codeInsight.intentions.argument

import org.jetbrains.plugins.scala.codeInsight.intention.argument.AddNameToArgumentIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 5/14/12
 */

class AddNameToArgumentIntentionTest extends ScalaIntentionTestBase {
  def familyName = AddNameToArgumentIntention.familyName

  def test() {
    val text =
      """
        |class NameParameters {
        |  def doSomething(flag: Boolean) {}
        |
        |  doSomething(t<caret>rue)
        |}
      """
    val resultText =
      """
        |class NameParameters {
        |  def doSomething(flag: Boolean) {}
        |
        |  doSomething(flag = t<caret>rue)
        |}
      """

    doTest(text, resultText)
  }

  def test2() {
    val text =
      """
        |class NameParameters {
        |  def doSomething(flag: Boolean, a: Int) {}
        |
        |  doSomething(t<caret>rue, 8)
        |}
      """
    val resultText =
      """
        |class NameParameters {
        |  def doSomething(flag: Boolean, a: Int) {}
        |
        |  doSomething(flag = t<caret>rue, a = 8)
        |}
      """

    doTest(text, resultText)
  }

  def test3() {
    val text =
      """
        |class NameParameters {
        |  def doSomething(flag: Boolean, a: Int, b: Int) {}
        |
        |  doSomething(true, 8, <caret>9)
        |}
      """
    val resultText =
      """
        |class NameParameters {
        |  def doSomething(flag: Boolean, a: Int, b: Int) {}
        |
        |  doSomething(true, 8, <caret>b = 9)
        |}
      """

    doTest(text, resultText)
  }

}