package org.jetbrains.plugins.scala.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.scala.util.CollectingLoggedMessagesProcessor.collectMatchingErrors
import org.junit.Assert.{assertEquals, assertTrue}

class CollectingLoggedMessagesProcessorTest extends BasePlatformTestCase {

  import CollectingLoggedMessagesProcessorTest._

  def testCollectMatchingErrorsSwallowsMatchedError(): Unit = {
    val (_, loggedErrors) = collectMatchingErrors(_.allPartsConcatenatedText.contains("matching message")) {
      Log.error("matching message", new RuntimeException("matching throwable"), "matching details")
    }

    assertEquals(1, loggedErrors.size)
    assertEquals(s"#${classOf[CollectingLoggedMessagesProcessorTest].getName}", loggedErrors.head.category)
  }

  def testLoggedErrorTextContainsMessageDetailsAndThrowableText(): Unit = {
    val (_, loggedErrors) = collectMatchingErrors(_.allPartsConcatenatedText.contains("message text")) {
      Log.error("message text", new RuntimeException("throwable text"), "details text")
    }

    val text = loggedErrors.head.allPartsConcatenatedText
    assertTrue(text.contains("message text"))
    assertTrue(text.contains("details text"))
    assertTrue(text.contains("throwable text"))
    assertTrue(text.contains("java.lang.RuntimeException: throwable text"))
  }

  def testCollectMatchingErrorsKeepsDefaultBehaviorForUnmatchedError(): Unit = {
    val processor = new CollectingLoggedMessagesProcessor(
      _.allPartsConcatenatedText.contains("different message"),
      LoggedErrorProcessor.Action.NONE,
    )

    val actions = processor.processError(
      category = "category",
      message = "unmatched message",
      details = Array.empty,
      t = new RuntimeException("unmatched throwable")
    )

    assertEquals(LoggedErrorProcessor.Action.ALL, actions)
    assertTrue(processor.errors.isEmpty)
  }
}

private object CollectingLoggedMessagesProcessorTest {
  private val Log = Logger.getInstance(classOf[CollectingLoggedMessagesProcessorTest])
}
