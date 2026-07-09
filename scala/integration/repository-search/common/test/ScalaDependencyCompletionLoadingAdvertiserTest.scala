package org.jetbrains.plugins.scala.reposearch.common

import com.intellij.codeInsight.completion.{CompletionResultSet, CompletionSorter, PlainPrefixMatcher, PrefixMatcher}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation}
import com.intellij.patterns.ElementPattern
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.{LOCAL, SERVER}
import com.intellij.repository.search.completion.api.{DependencyCompletionContributionSource, DependencyCompletionEvent, DependencyCompletionResult}
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.{assertEquals, assertSame, assertTrue}
import org.junit.jupiter.api.Test

import scala.collection.mutable

/**
 * Based on `com.intellij.gradle.completion.DependencyCompletionLoadingAdvertiserTest`.
 */
//noinspection ApiStatus,UnstableApiUsage
@TestApplication
class ScalaDependencyCompletionLoadingAdvertiserTest {
  @Test
  def placeholderIsAddedWhenServerFailedAndNoResultsInNonFreeMode(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(serverFailed())

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(1, resultSet.lookupElements.size)
    assertEquals(
      ScalaRepoSearchBundle.message("scala.dependency.completion.server.unavailable.short"),
      resultSet.renderFirstLookupElementText(),
    )
  }

  @Test
  def placeholderUsesTimeoutTextAfterServerTimedOutEvent(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(serverTimedOut())

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(1, resultSet.lookupElements.size)
    assertEquals(
      ScalaRepoSearchBundle.message("scala.dependency.completion.server.timeout.short"),
      resultSet.renderFirstLookupElementText(),
    )
  }

  @Test
  def placeholderIsAddedViaTheAlwaysTruePrefixMatcher(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(serverFailed())

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(1, resultSet.requestedPrefixMatchers.size)
    assertSame(PlainPrefixMatcher.ALWAYS_TRUE, resultSet.requestedPrefixMatchers.head)
  }

  @Test
  def noPlaceholderIsAddedWhenHadResultsIsTrue(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(serverFailed())

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = true)

    assertTrue(resultSet.lookupElements.isEmpty)
  }

  @Test
  def noPlaceholderIsAddedForAutoPopupCompletion(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(serverFailed())

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = true, hadResults = false)

    assertTrue(resultSet.lookupElements.isEmpty)
  }

  @Test
  def noPlaceholderIsAddedInFreeMode(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = true)
    advertiser.onEvent(serverFailed())

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertTrue(resultSet.lookupElements.isEmpty)
  }

  @Test
  def noPlaceholderIsAddedWithoutATerminalServerStatus(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.showSearchingStatus()
    advertiser.onComplete()

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertTrue(resultSet.lookupElements.isEmpty)
  }

  @Test
  def itemEventsDoNotRecordATerminalServerStatus(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(localItem())
    advertiser.onEvent(serverItem())
    advertiser.onComplete()

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertTrue(resultSet.lookupElements.isEmpty)
  }

  @Test
  def serverFailedAfterItemEventsStillRecordsTheTerminalStatus(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(localItem())
    advertiser.onEvent(serverFailed(new RuntimeException("network")))

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(1, resultSet.lookupElements.size)
    assertEquals(
      ScalaRepoSearchBundle.message("scala.dependency.completion.server.unavailable.short"),
      resultSet.renderFirstLookupElementText(),
    )
  }

  @Test
  def serverFailedAfterServerTimedOutOverridesTheRecordedStatus(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(serverTimedOut())
    advertiser.onEvent(serverFailed())

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(
      ScalaRepoSearchBundle.message("scala.dependency.completion.server.unavailable.short"),
      resultSet.renderFirstLookupElementText(),
    )
  }

  @Test
  def serverTimedOutAfterServerFailedDoesNotOverrideTheRecordedStatus(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    advertiser.onEvent(serverFailed())
    advertiser.onEvent(serverTimedOut())

    val resultSet = new TestCompletionResultSet
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(
      ScalaRepoSearchBundle.message("scala.dependency.completion.server.unavailable.short"),
      resultSet.renderFirstLookupElementText(),
    )
  }

  @Test
  def lifecycleMethodsDoNotThrowWhenThereIsNoActiveCompletionSession(): Unit = {
    val advertiser = new ScalaDependencyCompletionLoadingAdvertiser(isFreeMode = false)
    // CompletionServiceImpl.getCurrentCompletionProgressIndicator is null in unit tests,
    // so all ad-update calls should silently no-op.
    advertiser.showSearchingStatus()
    advertiser.onEvent(localItem())
    advertiser.onEvent(serverItem())
    advertiser.onEvent(serverFailed())
    advertiser.onEvent(serverTimedOut())
    advertiser.onComplete()
  }
}

private def item(source: DependencyCompletionContributionSource): DependencyCompletionEvent[DependencyCompletionResult] =
  new DependencyCompletionEvent.Item(new DependencyCompletionResult("g", "a", "1.0", null, source))

private def localItem(): DependencyCompletionEvent[DependencyCompletionResult] = item(LOCAL)

private def serverItem(): DependencyCompletionEvent[DependencyCompletionResult] = item(SERVER)

private def serverFailed(cause: Throwable = null): DependencyCompletionEvent.ServerFailed =
  new DependencyCompletionEvent.ServerFailed(cause)

private def serverTimedOut(): DependencyCompletionEvent.ServerTimedOut =
  DependencyCompletionEvent.ServerTimedOut.INSTANCE

private final class TestCompletionResultSet extends CompletionResultSet(new PlainPrefixMatcher(""), _ => (), null) {
  val lookupElements: mutable.ListBuffer[LookupElement] = mutable.ListBuffer.empty
  val requestedPrefixMatchers: mutable.ListBuffer[PrefixMatcher] = mutable.ListBuffer.empty

  override def addElement(element: LookupElement): Unit = lookupElements += element

  override def withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet = {
    requestedPrefixMatchers += matcher
    this
  }

  override def withPrefixMatcher(prefix: String): CompletionResultSet =
    withPrefixMatcher(new PlainPrefixMatcher(prefix))

  override def withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet = this

  override def addLookupAdvertisement(text: String): Unit = ()

  override def caseInsensitive(): CompletionResultSet = this

  override def restartCompletionOnPrefixChange(prefixCondition: ElementPattern[String]): Unit = ()

  override def restartCompletionWhenNothingMatches(): Unit = ()

  def renderFirstLookupElementText(): String = {
    val element = lookupElements.head
    val presentation = new LookupElementPresentation
    element.renderElement(presentation)
    presentation.getItemText
  }
}
