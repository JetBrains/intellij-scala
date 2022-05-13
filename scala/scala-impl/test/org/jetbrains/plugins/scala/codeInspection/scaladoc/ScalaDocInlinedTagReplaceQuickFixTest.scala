package org.jetbrains.plugins.scala.codeInspection.scaladoc

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class ScalaDocInlinedTagReplaceQuickFixTest extends ScalaInspectionTestBase {

  override protected val classOfInspection =
    classOf[ScalaDocInlinedTagInspection]

  override protected val description =
    "Inlined Tag"

  private val hint =
    "Replace inlined tag with monospace wiki syntax"

  def testReplaceInlinedTagWithMonospace_Links(): Unit = {
    testQuickFixAllInFile(
      """/**
        | * {@link java.lang.Exception}<br>
        | * {@link java.lang.Exception my description 1}<br>
        | * {@linkplain java.lang.Exception}<br>
        | * {@linkplain java.lang.Exception my description 2}<br>
        | */
        |""".stripMargin,
      """/**
        | * [[java.lang.Exception]]<br>
        | * [[java.lang.Exception my description 1]]<br>
        | * [[java.lang.Exception]]<br>
        | * [[java.lang.Exception my description 2]]<br>
        | */
        |""".stripMargin,
      hint
    )
  }

  def testEscapeTextFromInlineTag(): Unit = {
    testQuickFix(
      """/**
        | * {@code ` ^ __ ''' '' ,, [[ =}
        | */
        |""".stripMargin,
      """/**
        | * <pre>&#96; &#94; &#95;&#95; &#39;&#39;&#39; &#39;&#39; &#44;&#44; &#91;&#91; &#61;</pre>
        | */
        |""".stripMargin,
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