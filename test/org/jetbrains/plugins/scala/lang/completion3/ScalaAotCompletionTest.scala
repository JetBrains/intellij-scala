package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.lang.completion3.ScalaAotCompletionTest._
import org.junit.Assert

/**
 * @author Pavel Fatin
 */
class ScalaAotCompletionTest extends ScalaCodeInsightTestBase {
  def testParameterName() {
    val before =
      """
      |object Dummy {
      |  class Foo
      |  def f(f<caret>)
      |}
      """

    val after =
      """
        |object Dummy {
        |  class Foo
        |  def f(foo: Foo<caret>)
        |}
      """

    test(before, after) {
      val lookups = complete(1, CompletionType.BASIC)
      Assert.assertTrue(lookups.exists(_.getLookupString == "Foo"))
      finishLookup(findByText(lookups, "foo: Foo"))
    }
  }

  def testValueName() {
    val before =
      """
      |object Dummy {
      |  class Foo
      |  val f<caret>
      |}
      """

    val after =
      """
        |object Dummy {
        |  class Foo
        |  val foo<caret>
        |}
      """

    test(before, after) {
      val lookups = complete(1, CompletionType.BASIC)
      Assert.assertTrue(lookups.exists(_.getLookupString == "Foo"))
      finishLookup(findByText(lookups, "foo"))
    }
  }

  def testVariableName() {
    val before =
      """
      |class Foo
      |var f<caret>
      """

    val after =
      """
        |class Foo
        |var foo<caret>
      """

    test(before, after) {
      val lookups = complete(1, CompletionType.BASIC)
      Assert.assertTrue(lookups.exists(_.getLookupString == "Foo"))
      finishLookup(findByText(lookups, "foo"))
    }
  }

  def testPartialName() {
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
      val lookups = complete(1, CompletionType.BASIC)
      Assert.assertTrue(lookups.exists(_.getLookupString == "FooBarMoo"))
      finishLookup(findByText(lookups, "barMoo: FooBarMoo"))
    }
  }

  def testImport() {
    val before =
      """
        |def f(rectangle<caret>)
      """

    val after =
      """
        |import java.awt.Rectangle
        |
        |def f(rectangle: Rectangle<caret>)
      """

    test(before, after) {
      val lookups = complete(1, CompletionType.BASIC)
      Assert.assertTrue(lookups.exists(_.getLookupString == "Rectangle"))
      finishLookup(findByText(lookups, "rectangle: Rectangle"))
    }
  }

  private def test(before: String, after: String)(actions: => Unit) {
    configureFromFileTextAdapter("dummy.scala", before.stripMargin('|').replaceAll("\r", "").trim())
    actions
    checkResultByText(after.stripMargin('|').replaceAll("\r", "").trim())
  }
}

object ScalaAotCompletionTest {
  def findByText(elements: Seq[LookupElement], text: String): LookupElement = {
    findByText0(elements, text).getOrElse {
      Assert.fail("No such element: " + text)
      null // unreachable
    }
  }

  def findByText0(elements: Seq[LookupElement], text: String): Option[LookupElement] = {
    elements.find { element =>
      val presentation = new LookupElementPresentation()
      element.renderElement(presentation)
      presentation.getItemText == text
    }
  }
}