package org.jetbrains.plugins.scala.codeInspection.scaladoc

import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase

class ScalaDocInlinedTagReplaceQuickFixTest extends ScalaQuickFixTestBase {

  override protected val classOfInspection =
    classOf[ScalaDocInlinedTagInspection]

  override protected val description =
    "Inlined Tag"

  private val hint =
    "Replace inlined tag with monospace wiki syntax"

  def testReplaceInlinedTagWithMonospace_Reference(): Unit = {
    testQuickFix(
      """/** {@link java.lang.Exception} */""",
      """/** `java.lang.Exception` */""",
      hint
    )
  }

  def testReplaceInlinedTagWithMonospace_Text(): Unit = {
    testQuickFix(
      """/** {@author some guy} */""",
      """/** `some guy` */""",
      hint
    )
  }

  def testReplaceInlinedTagWithMonospace_EmptyValue(): Unit = {
    testQuickFix(
      """/** {@author} */""",
      """/** `` */""",
      hint
    )
  }
}