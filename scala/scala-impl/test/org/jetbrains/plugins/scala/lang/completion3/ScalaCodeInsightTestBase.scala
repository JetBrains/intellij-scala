package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{Lookup, LookupElement, LookupElementPresentation, LookupManager}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.junit.Assert._

/**
 * @author Alexander Podkhalyuzin
 */
abstract class ScalaCodeInsightTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import CompletionType.BASIC
  import Lookup.REPLACE_SELECT_CHAR
  import ScalaCodeInsightTestBase._

  protected override def setUp(): Unit = {
    super.setUp()
    StatisticsManager.getInstance match {
      case impl: StatisticsManagerImpl => impl.enableStatistics(getTestRootDisposable)
    }
  }

  override def getTestDataPath: String =
    s"${super.getTestDataPath}completion3/"

  //it make tests slower, so let's enable it only for ScalaBasicCompletionTest
  protected def needRetypeLine: Boolean = false

  protected final def activeLookupWithItems(fileText: String,
                                            completionType: CompletionType = BASIC,
                                            invocationCount: Int = DEFAULT_TIME)
                                           (items: LookupImpl => Iterable[LookupElement] = allItems) = {
    configureFromFileText(fileText)

    if (needRetypeLine) {
      retypeLineBeforeCaret()
    }

    changePsiAt(getEditor.getCaretModel.getOffset)

    new CodeCompletionHandlerBase(completionType, false, false, true)
      .invokeCompletion(getProject, getEditor, invocationCount)

    LookupManager.getActiveLookup(getEditor) match {
      case impl: LookupImpl => (impl, items(impl))
      case _ => throw new AssertionError("Lookups not found")
    }
  }

  //retype line with completion on every char
  private def retypeLineBeforeCaret(): Unit = {
    invokeAndWait {
      val caretModel = getEditor.getCaretModel
      val caretOffset = caretModel.getOffset

      val document = getEditor.getDocument
      val lineStart = document.getLineStartOffset(document.getLineNumber(caretOffset))

      val beforeLineStart = document.getText(TextRange.create(0, lineStart))
      val lineStartText   = document.getText(TextRange.create(lineStart, caretOffset))
      val afterCaret      = document.getText(TextRange.create(caretOffset, document.getTextLength))

      if (!hasOpeningBracesOrQuotes(lineStartText)) { //todo: disable typed handlers?
        inWriteAction {
          document.setText(beforeLineStart + afterCaret)
        }

        caretModel.moveToOffset(lineStart)

        val completionHandler =
          new CodeCompletionHandlerBase(CompletionType.BASIC,
            /*invokedExplicitly*/ false,
            /*autopopup*/ true,
            /*synchronous*/ true)

        for (char <- lineStartText) {
          myFixture.`type`(char)
          commit(document)

          completionHandler.invokeCompletion(getProject, getEditor, 0)
        }

        caretModel.moveToOffset(caretOffset)

        println("Start of the line was retyped")
      }
    }
  }

  private def hasOpeningBracesOrQuotes(text: String): Boolean = "{([<\"\'".exists(text.contains(_))

  private def commit(document: Document): Unit =
    PsiDocumentManager.getInstance(getProject).commitDocument(getEditor.getDocument)

  protected final def doCompletionTest(fileText: String,
                                       resultText: String,
                                       item: String,
                                       char: Char = REPLACE_SELECT_CHAR,
                                       time: Int = DEFAULT_TIME,
                                       completionType: CompletionType = BASIC): Unit =
    doRawCompletionTest(fileText, resultText, char, time, completionType) {
      hasLookupString(_, item)
    }

  protected final def doRawCompletionTest(fileText: String,
                                          resultText: String,
                                          char: Char = REPLACE_SELECT_CHAR,
                                          invocationCount: Int = DEFAULT_TIME,
                                          completionType: CompletionType = BASIC)
                                         (predicate: LookupElement => Boolean = Function.const(true)): Unit = {
    val (lookup, items) = activeLookupWithItems(fileText, completionType, invocationCount)()

    items.find(predicate) match {
      case Some(item) =>
        lookup.finishLookup(char, item)
        checkResultByText(resultText)
      case _ => fail("Lookup not found")
    }
  }

  protected final def checkNoBasicCompletion(fileText: String, item: String): Unit =
    checkNoCompletion(fileText) {
      hasLookupString(_, item)
    }

  protected final def checkNoCompletion(fileText: String,
                                        `type`: CompletionType = BASIC,
                                        invocationCount: Int = DEFAULT_TIME)
                                       (predicate: LookupElement => Boolean = Function.const(true)): Unit = {
    configureFromFileText(fileText)

    val lookups = getFixture.complete(`type`, invocationCount)
    assertFalse(lookups != null && lookups.exists(predicate))
  }

  protected final def completeBasic(invocationCount: Int) = {
    assertNotEquals("Please use `completeBasic`", 1, invocationCount)

    val lookups = getFixture.complete(BASIC, invocationCount)
    assertNotNull(lookups)
    lookups
  }

  protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit =
    getFixture.checkResult(normalize(expectedFileText), ignoreTrailingSpaces)
}

object ScalaCodeInsightTestBase {

  val DEFAULT_TIME: Int = 1

  object LookupString {

    def unapply(lookup: LookupElement) = Some(lookup.getLookupString)
  }

  def hasLookupString(lookup: LookupElement, lookupString: String): Boolean =
    lookup.getLookupString == lookupString

  def hasItemText(lookup: LookupElement,
                  lookupString: String)
                 (itemText: String = lookupString,
                  itemTextItalic: Boolean = false,
                  itemTextBold: Boolean = false,
                  tailText: String = null,
                  grayed: Boolean = false): Boolean = lookup match {
    case LookupString(`lookupString`) =>
      val presentation = new LookupElementPresentation
      lookup.renderElement(presentation)
      presentation.getItemText == itemText &&
        presentation.isItemTextItalic == itemTextItalic &&
        presentation.isItemTextBold == itemTextBold &&
        presentation.getTailText == tailText &&
        presentation.isTailGrayed == grayed
    case _ => false
  }

  private def allItems(impl: LookupImpl) = {
    import collection.JavaConverters._
    impl.getItems.asScala
  }
}