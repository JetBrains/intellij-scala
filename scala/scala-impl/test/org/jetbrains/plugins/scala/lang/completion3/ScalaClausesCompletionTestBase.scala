package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.lookup.LookupElement

trait ScalaClausesCompletionTestBase extends ScalaCodeInsightTestBase {

  import ScalaCodeInsightTestBase.{DEFAULT_TIME, hasItemText}
  import completion.ScalaKeyword.{CASE, MATCH}

  protected def doPatternCompletionTest(fileText: String, resultText: String, itemText: String): Unit =
    doRawCompletionTest(fileText, resultText) {
      isPattern(_, itemText)
    }

  protected def doClauseCompletionTest(fileText: String, resultText: String,
                                       itemText: String,
                                       invocationCount: Int = DEFAULT_TIME): Unit =
    doRawCompletionTest(fileText, resultText, invocationCount = invocationCount) {
      isCaseClause(_, itemText)
    }

  protected def doMatchCompletionTest(fileText: String, resultText: String,
                                      invocationCount: Int = DEFAULT_TIME): Unit =
    doRawCompletionTest(fileText, resultText, invocationCount = invocationCount)(isExhaustiveMatch)

  protected def doCaseCompletionTest(fileText: String, resultText: String): Unit =
    doRawCompletionTest(fileText, resultText)(isExhaustiveCase)

  protected def isPattern(lookup: LookupElement, itemText: String) =
    hasItemText(lookup, itemText)(itemTextItalic = true)

  protected def isCaseClause(lookup: LookupElement, itemText: String) =
    hasItemText(lookup, CASE + itemText)(
      itemText = CASE,
      itemTextBold = true,
      tailText = " " + itemText
    )

  protected def isExhaustiveMatch(lookup: LookupElement) =
    isExhaustive(lookup, MATCH)

  protected def isExhaustiveCase(lookup: LookupElement) =
    isExhaustive(lookup, CASE)

  private[this] def isExhaustive(lookup: LookupElement, lookupString: String) =
    hasItemText(lookup, lookupString)(
      itemTextBold = true,
      tailText = " " + completion.clauses.ExhaustiveMatchCompletionContributor.rendererTailText,
      grayed = true
    )
}
