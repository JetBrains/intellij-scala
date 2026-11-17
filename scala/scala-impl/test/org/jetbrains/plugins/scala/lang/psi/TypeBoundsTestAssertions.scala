package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import org.junit.Assert.{assertEquals, assertSame}

/** Shares bound text assertions and separate AST-only ownership checks. */
object TypeBoundsTestAssertions {
  /** Checks both presence and text using only the supplied elements and description. */
  def assertBoundElementTexts(
    context: String,
    lowerElement: Option[PsiElement],
    upperElement: Option[PsiElement],
    expectedLowerText: Option[String],
    expectedUpperText: Option[String]
  ): Unit = {
    assertBoundElementText(context, "lower", lowerElement, expectedLowerText)
    assertBoundElementText(context, "upper", upperElement, expectedUpperText)
  }

  private def assertBoundElementText(
    context: String,
    side: String,
    element: Option[PsiElement],
    expectedText: Option[String]
  ): Unit = {
    val actualText = element.map(_.getText)
    assertEquals(
      s"$context: $side bound (presence and text)",
      describeBoundText(expectedText),
      describeBoundText(actualText)
    )
  }

  private def describeBoundText(text: Option[String]): String = text match {
    case Some(value) => s"present with text `$value`"
    case None => "absent"
  }

  /** Checks direct AST parents; keep this out of stub-only assertion paths. */
  def assertDirectBoundOwnership(
    context: String,
    owner: PsiElement,
    lowerElement: Option[PsiElement],
    upperElement: Option[PsiElement]
  ): Unit = {
    lowerElement.foreach { bound =>
      assertDirectBoundParent(context, "lower", owner, bound)
    }

    upperElement.foreach { bound =>
      assertDirectBoundParent(context, "upper", owner, bound)
    }
  }

  private def assertDirectBoundParent(
    context: String,
    side: String,
    expectedOwner: PsiElement,
    bound: PsiElement
  ): Unit = {
    val actualParent = bound.getParent
    assertSame(
      s"""$context: $side bound `${bound.getText}` has the wrong direct parent.
         |Expected parent: ${describeAstElement(expectedOwner)}
         |Actual parent: ${describeAstElement(actualParent)}""".stripMargin,
      expectedOwner,
      actualParent
    )
  }

  private def describeAstElement(element: PsiElement): String = {
    if (element == null) "<no parent>"
    else {
      val source = element.getText.replace("\r", "\\r").replace("\n", "\\n")
      s"${element.getClass.getSimpleName} at offset ${element.getTextOffset}: `$source`"
    }
  }
}
