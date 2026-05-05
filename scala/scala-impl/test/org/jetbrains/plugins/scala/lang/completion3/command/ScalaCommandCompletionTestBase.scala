package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.{Lookup, LookupElement, LookupEvent}
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import junit.framework.TestCase.{assertEquals, assertNotNull, fail}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.DefaultInvocationCount
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestFixture.lookupItemsDebugText
import org.jetbrains.plugins.scala.util.Markers

//noinspection ApiStatus,UnstableApiUsage
abstract class ScalaCommandCompletionTestBase extends ScalaCompletionTestBase with Markers {
  protected override def setUp(): Unit = {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable)
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable)
  }

  /**
   * @param prefix <code>.</code> or <code>..</code> before [[CARET]]. Should be provided in cases when caret is inside [[start]] and [[end]].
   *               Otherwise, doesn't really matter. Default: <code>.</code>
   */
  protected final def doCommandCompletionTest(fileText: String,
                                              predicate: LookupElement => Boolean,
                                              @Nullable resultText: String = null,
                                              finishLookup: Boolean = true,
                                              checkPreview: IntentionPreviewInfo => Unit = _ => (),
                                              prefix: String = ".",
                                              invocationCount: Int = DefaultInvocationCount): CommandCompletionLookupElement = {
    val (cleanText, expectedHighlightings) = extractMarker(fileText, caretMarker = Some(prefix + CARET))
    configureFromFileText(cleanText)
    val checkResult = resultText != null
    val elements = scalaCompletionTestFixture.complete(CompletionType.BASIC, invocationCount)
    val selectedItem = selectLookupItem(elements, selectCommandCompletions(predicate), finishLookup = checkResult || finishLookup)
    val lookup = asCommandCompletionLookup(selectedItem)
    assertNotNull(lookup)

    expectedHighlightings match {
      case Seq(expectedHighlightedRange, rest@_*) =>
        if (rest.nonEmpty) {
          fail(s"Too many ranges selected in the `fileText`. Expected one or none, got: ${expectedHighlightings.size}")
        }
        val actualHighlightedRange = lookup.getHighlighting.toOption.map(_.getRange).orNull
        assertEquals(expectedHighlightedRange, actualHighlightedRange)
      case _ =>
    }

    val preview = lookup.getCommand.getPreview
    checkPreview(preview)

    if (checkResult) {
      checkResultByText(resultText)
    }

    lookup
  }

  protected final def checkHasCommandCompletions(fileText: String, invocationCount: Int = DefaultInvocationCount): Unit = {
    configureFromFileText(fileText)
    val elements = scalaCompletionTestFixture.complete(CompletionType.BASIC, invocationCount)
    val commandCompletionElements = elements.filter(isCommandCompletionLookup)
    if (commandCompletionElements.isEmpty) {
      fail(
        s"""No command completion lookup elements found.
           |All elements:
           |${lookupItemsDebugText(elements)}""".stripMargin)
    }
  }

  protected final def checkNoCommandCompletionAtAll(fileText: String, invocationCount: Int = DefaultInvocationCount): Unit =
    scalaCompletionTestFixture.checkNoCompletion(fileText, CompletionType.BASIC, invocationCount)(predicate = isCommandCompletionLookup)

  protected final def checkNoCommandCompletion(fileText: String, predicate: LookupElement => Boolean,
                                               invocationCount: Int = DefaultInvocationCount): Unit =
    scalaCompletionTestFixture.checkNoCompletion(fileText, CompletionType.BASIC, invocationCount)(predicate = selectCommandCompletions(predicate))

  protected final def selectLookupItem(elements: Array[LookupElement], predicate: LookupElement => Boolean,
                                       finishLookup: Boolean = true, completionChar: Char = Lookup.AUTO_INSERT_SELECT_CHAR): LookupElement =
    scalaCompletionTestFixture.withActiveLookup { lookup =>
      elements.find(predicate) match {
        case Some(currentItem) =>
          lookup.setCurrentItem(currentItem)
          if (finishLookup) {
            if (LookupEvent.isSpecialCompletionChar(completionChar))
              lookup.finishLookup(completionChar)
            else
              scalaCompletionTestFixture.typeChar(completionChar)
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
          }
          currentItem
        case None =>
          fail(
            s"""Lookup element not found
               |All lookup elements:
               |${lookupItemsDebugText(elements)}""".stripMargin
          ).asInstanceOf[Nothing]
      }
    }

  protected final def selectCommandCompletions(delegate: LookupElement => Boolean): LookupElement => Boolean =
    element => isCommandCompletionLookup(element) && delegate(element)

  @Nullable
  protected final def asCommandCompletionLookup(element: LookupElement): CommandCompletionLookupElement =
    // use `as` instead of `isInstanceOf` to handle decorators such as PrioritizedLookupElement[CommandCompletionLookupElement]
    element.as(classOf[CommandCompletionLookupElement])

  protected final def isCommandCompletionLookup(element: LookupElement): Boolean =
    asCommandCompletionLookup(element) != null

  protected final def lookupStringContains(element: LookupElement, prefix: String, ignoreCase: Boolean = true): Boolean = {
    val lookupString = element.getLookupString
    if (ignoreCase) lookupString.toLowerCase.contains(prefix.toLowerCase)
    else lookupString.contains(prefix)
  }
}
