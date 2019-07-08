package org.jetbrains.plugins.scala
package codeInsight
package intentions
package parentheses

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * test only removing clarifying paretheses here
 *
 * Nikolay.Tropin
 * 6/27/13
 */
class RemoveUnnecessaryParenthesesIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = InspectionBundle.message("remove.unnecessary.parentheses.fix", "")

  def test_1(): Unit = doTest(
    s"1 + (1 * 2$caretTag)",
    "1 + 1 * 2"
  )

  def test_2(): Unit = doTest(
    s"1 :: (${caretTag}2 :: Nil)",
    "1 :: 2 :: Nil"
  )

  def test_3(): Unit = doTest(
    s"(- 1$caretTag) + 1",
    "-1 + 1"
  )

  def test_4(): Unit = doTest(
    s"(None$caretTag filter (_ => true)) headoption",
    "None filter (_ => true) headoption"
  )
}
