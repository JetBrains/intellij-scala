package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.lang.completion3.ScalaParameterCompletionTest._
import org.junit.Assert

/**
 * @author Pavel Fatin
 */
class ScalaParameterCompletionTest extends ScalaCodeInsightTestBase {
  def testParameterName() {
    val before =
      """
      |class Foo
      |def f(f<caret>)
      """

    val after =
      """
        |class Foo
        |def f(foo: Foo<caret>)
      """

    test(before, after) {
      val (activeLookup, _) = complete(1, CompletionType.BASIC)
      Assert.assertTrue(activeLookup.exists(_.getLookupString == "Foo"))
      completeLookupItem(findByText(activeLookup, "foo: Foo"))
    }
  }

  def testParameterPartialName() {
    val before =
      """
        |class FooBarMoo
        |def f(ba<caret>)
      """

    val after =
      """
        |class FooBarMoo
        |def f(barMoo: FooBarMoo<caret>)
      """

    test(before, after) {
      val (activeLookup, _) = complete(1, CompletionType.BASIC)
      Assert.assertTrue(activeLookup.exists(_.getLookupString == "FooBarMoo"))
      completeLookupItem(findByText(activeLookup, "barMoo: FooBarMoo"))
    }
  }

  private def test(before: String, after: String)(actions: => Unit) {
    configureFromFileTextAdapter("dummy.scala", before.stripMargin('|').replaceAll("\r", "").trim())
    actions
    checkResultByText(after.stripMargin('|').replaceAll("\r", "").trim())
  }
}

object ScalaParameterCompletionTest {
  def findByText(elements: Array[LookupElement], text: String): LookupElement = {
    findByText0(elements, text).getOrElse {
      Assert.fail("No such element: " + text)
      null // unreachable
    }
  }

  def findByText0(elements: Array[LookupElement], text: String): Option[LookupElement] = {
    elements.find { element =>
      val presentation = new LookupElementPresentation()
      element.renderElement(presentation)
      presentation.getItemText == text
    }
  }
}