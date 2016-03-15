package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
  * Created by kate
  * on 3/11/16
  */
class ScalaOverrideCompletionTest extends ScalaCodeInsightTestBase {

  private val baseText =
    """
      |class Base {
      |  protected def foo(int: Int): Int = 45
      |  type StringType = String
      |  val intValue = 45
      |  var intVariable = 43
      |}
    """

  private def handleText(text: String): String = text.stripMargin.replaceAll("\r", "").trim()

    def testFunction() {
      val inText =
        """
          |class Inheritor extends Base {
          |   override def f<caret>
          |}
        """

      configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))

      val outText =
        """
          |class Inheritor extends Base {
          |  override def foo(int: Int): Int = super.foo(int)
          |}
        """

      complete(1, CompletionType.BASIC)
      completeLookupItem()
      checkResultByText(handleText(baseText + outText))
    }

    def testValue() {
      val inText =
        """
          |class Inheritor extends Base {
          |   override val intVa<caret>
          |}
        """
      configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
      val (activeLookup, _) = complete(1, CompletionType.BASIC)

      val outText =
        """
          |class Inheritor extends Base {
          |  override val intValue: Int = _
          |}
        """

      completeLookupItem(activeLookup.find(le => le.getLookupString.contains("intValue")).get, '\t')
      checkResultByText(handleText(baseText + outText))
    }

    def testVariable() {
      val inText =
        """
          |class Inheritor extends Base {
          |   override var i<caret>
          |}
        """
      configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
      val (activeLookup, _) = complete(1, CompletionType.BASIC)

      val outText =
        """
          |class Inheritor extends Base {
          |  override var intVariable: Int = _
          |}
        """

      completeLookupItem(activeLookup.find(le => le.getLookupString.contains("intVariable")).get, '\t')
      checkResultByText(handleText(baseText + outText))
    }

    def testJavaObjectMethod() {
      val inText =
        """
          |class Inheritor extends Base {
          |   override def e<caret>
          |}
        """
      configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
      val (activeLookup, _) = complete(1, CompletionType.BASIC)

      val outText =
        """
          |class Inheritor extends Base {
          |  override def equals(obj: scala.Any): Boolean = super.equals(obj)
          |}
        """

      completeLookupItem(activeLookup.find(le => le.getLookupString.contains("equals")).get, '\t')
      checkResultByText(handleText(baseText + outText))
    }

  def testOverrideKeword() {
    val inText =
      """
        |class Inheritor extends Base {
        |   over<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val outText =
      """
        |class Inheritor extends Base {
        |  override protected def foo(int: Int): Int = super.foo(int)
        |}
      """

    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("foo")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
}
