package org.jetbrains.plugins.scala
package codeInsight.intentions.booleans

import org.jetbrains.plugins.scala.codeInsight.intention.booleans.NegateComparisonIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 5/13/12
 */

class NegateComparisonIntentionTest extends ScalaIntentionTestBase {
  def familyName = NegateComparisonIntention.familyName

  def test() {
    val text = "if (a ==<caret> b) {}"
    val resultText = "if (!(a !=<caret> b)) {}"

    doTest(text, resultText)
  }

  def test2() {
    val text = "if (a ==<caret> b) {}"
    val resultText = "if (!(a !=<caret> b)) {}"

    doTest(text, resultText)
  }

  def test3() {
    val text = "if (a <caret>>= b) {}"
    val resultText = "if (!(a <caret>< b)) {}"

    doTest(text, resultText)
  }

  def test4() {
    val text = "if (a !=<caret> b) {}"
    val resultText = "if (!(a ==<caret> b)) {}"

    doTest(text, resultText)
  }

  def test5() {
    val text = "if (a <caret>< b) {}"
    val resultText = "if (!(a <caret>>= b)) {}"

    doTest(text, resultText)
  }

  def test6() {
    val text = "if (!(a <caret>< b)) {}"
    val resultText = "if (a <caret>>= b) {}"

    doTest(text, resultText)
  }

  def test7() {
    val text = "a =<caret>= b"
    val resultText = "!(a !<caret>= b)"

    doTest(text, resultText)
  }

  def test8() {
    val text = "if (!(!(a <caret>< b))) {}"
    val resultText = "if (!(a <caret>>= b)) {}"

    doTest(text, resultText)
  }
}