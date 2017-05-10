package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.extensions._

/**
 * User: Dmitry Naydanov
 * Date: 12/9/11
 */

class ScalaDocCompletionTest extends ScalaCodeInsightTestBase {
  protected def genericCompletionComparison(initialText: String, finalText: String, filter: LookupElement => Boolean) {
    val fileText = initialText.stripMargin('|').replaceAll("\r", "").trim()
    val resultText = finalText.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(2, CompletionType.BASIC)

    finishLookup(lookups.find(cv => filter(cv)).get)
    checkResultByText(resultText)
  }

  protected def genericCompletionComprasion(initialText: String,  finalText: String,  preferedLookupString: String) {
    genericCompletionComparison(initialText, finalText,
      (le: LookupElement) => le.getLookupString == preferedLookupString)
  }

  def testTagNameCompletion() {
    genericCompletionComprasion(
      """
      | /**
      |  * @par<caret>
      |  */
      | def f(i: Int) { }
      """,
      """
      | /**
      |  * @param
      |  */
      | def f(i: Int) { }
      """,
      "param"
    )
  }

  def testTagValueCompletion() {
    genericCompletionComprasion(
      """
      | /**
      |  * @param par<caret>
      |  */
      | def f(param: String) {}
      """,
      """
      | /**
      |  * @param param
      |  */
      | def f(param: String) {}
      """,
      "param"
    )
  }

  def testLinkCodeCompletion() {
    genericCompletionComparison(
      """
      | /**
      |  *
      |  * [[HashM<caret>
      |  */
      """,
      """
      | /**
      |  *
      |  * [[java.util.HashMap
      |  */
      """,
      (al: LookupElement) => al.getObject.asInstanceOf[PsiClass].qualifiedName == "java.util.HashMap"
    )
  }

  def testTagValueFilteredCompletion() {
    genericCompletionComprasion(
      """
      |/**
      | * @param iii
      | * @param i<caret>
      | */
      | def f(iii: Int, ikk: Int) {}
      """,
      """
      |/**
      | * @param iii
      | * @param ikk
      | */
      | def f(iii: Int, ikk: Int) {}
      """,
      "ikk"
    )
  }
}