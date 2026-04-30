package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.lookup.{Lookup, LookupElement, LookupEvent}
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import junit.framework.TestCase.fail
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.DefaultInvocationCount
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestFixture.lookupItemsDebugText

//noinspection ApiStatus,UnstableApiUsage
abstract class ScalaCommandCompletionTestBase extends ScalaCompletionTestBase {
  protected override def setUp(): Unit = {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable)
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable)
  }

  protected final def doCommandCompletionTestIgnoringResult(fileText: String,
                                                            predicate: LookupElement => Boolean,
                                                            invocationCount: Int = DefaultInvocationCount): Unit = {
    configureFromFileText(fileText)
    val elements = scalaCompletionTestFixture.complete(CompletionType.BASIC, invocationCount)
    // will throw en exception if no matching command completion lookup elements found
    selectLookupItem(elements, selectCommandCompletions(predicate))
  }

  protected final def doCommandCompletionTest(fileText: String, resultText: String,
                                              predicate: LookupElement => Boolean,
                                              invocationCount: Int = DefaultInvocationCount): Unit = {
    doCommandCompletionTestIgnoringResult(fileText, predicate, invocationCount)
    checkResultByText(resultText)
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
                                       completionChar: Char = Lookup.AUTO_INSERT_SELECT_CHAR): Unit =
    scalaCompletionTestFixture.withActiveLookup { lookup =>
      elements.find(predicate) match {
        case Some(currentItem) =>
          lookup.setCurrentItem(currentItem)
          if (LookupEvent.isSpecialCompletionChar(completionChar))
            lookup.finishLookup(completionChar)
          else
            scalaCompletionTestFixture.typeChar(completionChar)
          NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        case None =>
          fail(
            s"""Lookup element not found
               |All lookup elements:
               |${lookupItemsDebugText(elements)}""".stripMargin
          )
      }
    }

  protected final def selectCommandCompletions(delegate: LookupElement => Boolean): LookupElement => Boolean =
    element => isCommandCompletionLookup(element) && delegate(element)

  protected final def isCommandCompletionLookup(element: LookupElement): Boolean =
    // use `as` instead of `isInstanceOf` to handle decorators such as PrioritizedLookupElement[CommandCompletionLookupElement]
    element.as(classOf[CommandCompletionLookupElement]) != null

  protected final def lookupStringContains(element: LookupElement, prefix: String, ignoreCase: Boolean = true): Boolean = {
    val lookupString = element.getLookupString
    if (ignoreCase) lookupString.toLowerCase.contains(prefix.toLowerCase)
    else lookupString.contains(prefix)
  }
}
