package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.EditorTestUtil

/**
 * @author Ksenia.Sautina
 * @since 5/13/12
 */
class NegateComparisonIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = NegateComparisonIntention.FamilyName

  def test1(): Unit = {
    val text = s"if (a ==$CARET b) {}"
    val resultText = s"if (!(a !=$CARET b)) {}"

    doTest(text, resultText)
  }

  def test2(): Unit = {
    val text = s"if (a ==$CARET b) {}"
    val resultText = s"if (!(a !=$CARET b)) {}"

    doTest(text, resultText)
  }

  def test3(): Unit = {
    val text = s"if (a $CARET>= b) {}"
    val resultText = s"if (!(a $CARET< b)) {}"

    doTest(text, resultText)
  }

  def test4(): Unit = {
    val text = s"if (a !=$CARET b) {}"
    val resultText = s"if (!(a ==$CARET b)) {}"

    doTest(text, resultText)
  }

  def test5(): Unit = {
    val text = s"if (a $CARET< b) {}"
    val resultText = s"if (!(a $CARET>= b)) {}"

    doTest(text, resultText)
  }

  def test6(): Unit = {
    val text = s"if (!(a $CARET< b)) {}"
    val resultText = s"if (a $CARET>= b) {}"

    doTest(text, resultText)
  }

  def test7(): Unit = {
    val text = s"a =$CARET= b"
    val resultText = s"!(a !$CARET= b)"

    doTest(text, resultText)
  }

  def test8(): Unit = {
    val text = s"if (!(!(a $CARET< b))) {}"
    val resultText = s"if (!(a $CARET>= b)) {}"

    doTest(text, resultText)
  }
}