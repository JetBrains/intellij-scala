package org.jetbrains.plugins.scala.reposearch.common

import ScalaDependencyCompletionContributor.ScalaDependencyCompletionProjectSystemId
import ScalaDependencyCompletionProvider.{LookupText, logger}

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.CoroutinesKt.runBlockingCancellable
import com.intellij.repository.search.completion.api.*
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.packagesearch.lang.completion.BaseDependencyCompletionParameters

import javax.swing.Icon
import kotlinx.coroutines.flow.Flow
import scala.collection.mutable

trait ScalaDependencyCompletionProvider[Params <: BaseDependencyCompletionParameters[?]] {
  protected def lookupStringOnGroupId(params: Params, result: DependencyCompletionResult): Option[LookupText]

  protected def lookupStringOnArtifactId(params: Params, groupId: String, result: DependencyPartCompletionResult): Option[LookupText]

  protected def createLookupItem(params: Params, lookupText: LookupText, icon: Icon): LookupElement

  protected def finishDependencySuggestions(params: Params): Unit

  final def suggestCompletionOnGroupId(params: Params, groupIdPrefix: String): Unit = {
    // autocomplete the groupId/whole dependency only after 3 or more characters are typed
    // or if triggered explicitly by user
    if (params.completionParams.isAutoPopup && groupIdPrefix.lengthIs < 3) return

    val itemFlow: Flow[DependencyCompletionEvent[DependencyCompletionResult]] = createFlow(params) { (service, context) =>
      val request = new DependencyCompletionRequest(groupIdPrefix, context)
      service.suggestCompletions(request)
    }

    collectFlow(params, itemFlow) {
      case item: DependencyCompletionEvent.Item[DependencyCompletionResult] =>
        val result = item.getResult
        val maybeLookupText = lookupStringOnGroupId(params, result)
        maybeLookupText.map(_ -> result.getSource)
      case event =>
        logger.debug(s"Unexpected completion event: $event")
        None
    }
  }

  final def suggestCompletionOnArtifactId(params: Params, groupId: String, artifactIdPrefix: String): Unit = {
    val itemFlow: Flow[DependencyCompletionEvent[DependencyPartCompletionResult]] = createFlow(params) { (service, context) =>
      val request = new DependencyArtifactCompletionRequest(groupId, artifactIdPrefix, context)
      service.suggestArtifactCompletions(request)
    }

    collectFlow(params, itemFlow) {
      case item: DependencyCompletionEvent.Item[DependencyPartCompletionResult] =>
        val result = item.getResult
        val maybeLookupText = lookupStringOnArtifactId(params, groupId, result)
        maybeLookupText.map(_ -> result.getSource)
      case event =>
        logger.debug(s"Unexpected completion event: $event")
        None
    }
  }

  @Nullable
  private def createFlow[T <: BaseDependencyCompletionResult](
    params: Params,
  )(
    callService: (DependencyCompletionService, DependencyCompletionContext) => Flow[DependencyCompletionEvent[T]],
  ): Flow[DependencyCompletionEvent[T]] = {
    val completionService = ApplicationManager.getApplication.getService(classOf[DependencyCompletionService])
    if (completionService == null) null
    else {
      val context = new DependencyCompletionContextImpl(params.completionParams.getOriginalFile.getProject, ScalaDependencyCompletionProjectSystemId)
      callService(completionService, context)
    }
  }

  private def collectFlow[T <: BaseDependencyCompletionResult](
    params: Params,
    @Nullable itemFlow: Flow[DependencyCompletionEvent[T]]
  )(
    createLookupText: DependencyCompletionEvent[T] => Option[(LookupText, DependencyCompletionContributionSource)],
  ): Unit = if (itemFlow != null) {
    val loadingAdvertiser = new ScalaDependencyCompletionLoadingAdvertiser()
    loadingAdvertiser.showSearchingStatus()

    val seenLookupStrings = mutable.HashSet.empty[String]
    runBlockingCancellable { (_, continuation) =>
      itemFlow.collect({
        case (event, cont) =>
          loadingAdvertiser.onEvent(event)
          createLookupText(event).foreach { (lookupText, source) =>
            if (seenLookupStrings.add(lookupText.lookupString)) {
              val icon = source match {
                case DependencyCompletionContributionSource.LOCAL => AllIcons.Build.CompletionLocalCache
                case DependencyCompletionContributionSource.SERVER => AllIcons.Build.CompletionCloud
              }
              val lookupItem = createLookupItem(params, lookupText, icon)
              params.resultSet.addElement(lookupItem)
            }
          }
          cont
      }, continuation)
    }
    loadingAdvertiser.onComplete()
    loadingAdvertiser.addServerErrorPlaceholderIfNeeded(params.resultSet, params.completionParams.isAutoPopup, hadResults = seenLookupStrings.nonEmpty)
    finishDependencySuggestions(params)
  }
}

object ScalaDependencyCompletionProvider {
  private val logger: Logger = Logger.getInstance(classOf[ScalaDependencyCompletionProvider[?]])

  final case class LookupText(lookupString: String, presentableText: String)

  object LookupText {
    def apply(lookupString: String): LookupText = new LookupText(lookupString, lookupString)
  }
}
