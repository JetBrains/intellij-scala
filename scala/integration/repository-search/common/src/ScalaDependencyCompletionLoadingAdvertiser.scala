//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala.reposearch.common

import com.intellij.codeInsight.completion.LookupActionKeys.{SUPPRESS_QUICK_DEFINITION, SUPPRESS_QUICK_DOCUMENTATION}
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.completion.{CompletionProgressIndicator, CompletionResultSet, PlainPrefixMatcher}
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.repository.search.completion.api.{BaseDependencyCompletionResult, DependencyCompletionContributionSource, DependencyCompletionEvent}
import org.jetbrains.annotations.{ApiStatus, Nullable}

import scala.compiletime.uninitialized

/**
 * Manages loading advertisement messages in the completion popup during dependency completion.
 *
 * Shows a status message in the completion popup advertiser while server results are being fetched.
 * - Phase 1 (initial): "Searching for dependency libraries..."
 * - Phase 2 (local results arrived, server still pending): "Server is still being queried..."
 * - Phase 3 (server responded or timed out): "Press {ACTION_CODE_COMPLETION} to query the server"
 *
 * If the server request times out or fails, a dedicated message is shown in place of Phase 3:
 * - "Server query timed out. Press {ACTION_CODE_COMPLETION} to retry"
 * - "Server unavailable. Press {ACTION_CODE_COMPLETION} to retry"
 *
 * If the Ultimate plugin is disabled (no server access)
 * - Phase 1 (initial): "Searching for dependency libraries..."
 * - Phase 2 (all local results are shown): advertisement is cleared.
 *
 * Based on com.intellij.gradle.completion.DependencyCompletionLoadingAdvertiser
 */
@ApiStatus.Internal
final class ScalaDependencyCompletionLoadingAdvertiser(
  private val isFreeMode: Boolean = PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID),
) {
  private var serverResultsReceived: Boolean = isFreeMode
  @Nullable private var terminalServerStatus: TerminalServerStatus = uninitialized

  /**
   * Call before starting the flow collection to show the initial loading message.
   */
  def showSearchingStatus(): Unit = replaceAdvertisement(ScalaRepoSearchBundle.message("scala.dependency.completion.searching.status"))

  /**
   * Call for each event received from the completion flow.
   * Automatically updates or clears the loading message based on the result source.
   */
  def onEvent(event: DependencyCompletionEvent[?]): Unit = {
    event match {
      case item: DependencyCompletionEvent.Item[?] =>
        onItem(item)
      case DependencyCompletionEvent.ServerTimedOut.INSTANCE =>
        serverResultsReceived = true
        if (terminalServerStatus == null) terminalServerStatus = TerminalServerStatus.TIMED_OUT
        onComplete()
      case _: DependencyCompletionEvent.ServerFailed =>
        serverResultsReceived = true
        terminalServerStatus = TerminalServerStatus.UNAVAILABLE
        onComplete()
    }
  }

  /**
   * Call after the flow collection completes to ensure the loading message is removed.
   */
  def onComplete(): Unit = {
    if (isFreeMode) {
      clearAdvertisement()
      return
    }

    val shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION)
    if (shortcut.isBlank) {
      clearAdvertisement()
      return
    }

    terminalServerStatus match {
      case TerminalServerStatus.TIMED_OUT =>
        replaceAdvertisement(ScalaRepoSearchBundle.message("scala.dependency.completion.server.timeout", shortcut))
      case TerminalServerStatus.UNAVAILABLE =>
        replaceAdvertisement(ScalaRepoSearchBundle.message("scala.dependency.completion.server.unavailable", shortcut))
      case null =>
        clearAdvertisement()
    }
  }

  /**
   * If the server request ended in a terminal error (timeout / unavailable) and no items were
   * produced, add a placeholder item so the popup stays open with the advertiser
   * message — instead of being replaced with the "No suggestions" hint.
   *
   * No-op for auto-popup completion (where an empty popup would be intrusive),
   * for free mode (no server expected), and when at least one item was added.
   */
  def addServerErrorPlaceholderIfNeeded(
    resultSet: CompletionResultSet,
    isAutoPopup: Boolean,
    hadResults: Boolean,
  ): Unit = {
    if (hadResults || isAutoPopup || isFreeMode) return

    val placeholderText = terminalServerStatus match {
      case TerminalServerStatus.TIMED_OUT => ScalaRepoSearchBundle.message("scala.dependency.completion.server.timeout.short")
      case TerminalServerStatus.UNAVAILABLE => ScalaRepoSearchBundle.message("scala.dependency.completion.server.unavailable.short")
      case null => return
    }

    val placeholder = LookupElementBuilder.create("").withPresentableText(placeholderText)
    placeholder.putUserData(SUPPRESS_QUICK_DEFINITION, true)
    placeholder.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)
    resultSet.withPrefixMatcher(PlainPrefixMatcher.ALWAYS_TRUE).addElement(placeholder)
  }

  private def onItem(event: DependencyCompletionEvent.Item[? <: BaseDependencyCompletionResult]): Unit = {
    if (serverResultsReceived) return

    if (event.getResult.getSource == DependencyCompletionContributionSource.SERVER) {
      serverResultsReceived = true
      onComplete()
    } else {
      replaceAdvertisement(ScalaRepoSearchBundle.message("scala.dependency.completion.server.still.searching"))
    }
  }

  private def withCurrentCompletionProgressIndicator(f: CompletionProgressIndicator => Unit): Unit = {
    val indicator = CompletionServiceImpl.getCurrentCompletionProgressIndicator
    if (indicator != null) f(indicator)
  }

  private def replaceAdvertisement(@NlsContexts.PopupAdvertisement text: String): Unit =
    withCurrentCompletionProgressIndicator(_.replaceAllAdvertisements(text, null))

  private def clearAdvertisement(): Unit = withCurrentCompletionProgressIndicator(_.clearAllAdvertisements())
}

/**
 * Enum representing the terminal status of a call to the dependency completion service.
 *
 * If the dependency completion service throws an TimeoutCancellationException from any server contributor, TIME_OUT status is set.
 * If the service throws any other exception (except for cancellation) from any server contributor,
 * the UNAVAILABLE status is set. This overrides a TIME_OUT status if set.
 */
private enum TerminalServerStatus {
  case TIMED_OUT, UNAVAILABLE
}
