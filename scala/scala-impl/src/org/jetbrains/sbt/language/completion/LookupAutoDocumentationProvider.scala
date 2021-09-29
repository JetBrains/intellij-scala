package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.Alarm
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.language.completion.LookupAutoDocumentationProvider._
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder

final class LookupAutoDocumentationProvider extends LookupManagerListener {
  override def activeLookupChanged(oldLookup: Lookup, newLookup: Lookup): Unit =
    if (!isHeadlessEnv && !CodeInsightSettings.getInstance.AUTO_POPUP_JAVADOC_INFO && newLookup != null)
      newLookup.addLookupListener(lookupListener(newLookup))
}

object LookupAutoDocumentationProvider {
  private val alarm = new Alarm()

  private def isHeadlessEnv = ApplicationManager.getApplication.isHeadlessEnvironment

  private def lookupListener(lookup: Lookup) = new LookupListener {

    override def currentItemChanged(event: LookupEvent): Unit = {
      alarm.cancelAllRequests()

      if (event.getItem != null && event.getItem.getPsiElement.is[SbtScalacOptionDocHolder]) {
        val request: Runnable = () => showDocumentation()
        val delay = CodeInsightSettings.getInstance.JAVADOC_INFO_DELAY

        alarm.addRequest(request, delay)
      }
    }

    override def itemSelected(event: LookupEvent): Unit = lookupClosed()

    override def lookupCanceled(event: LookupEvent): Unit = lookupClosed()

    private def showDocumentation(): Unit = {
      val docManager = DocumentationManager.getInstance(lookup.getProject)
      // show docs only if not shown yet
      if (docManager.getDocInfoHint == null) {
        val currentItem = lookup.getCurrentItem
        val completion = CompletionService.getCompletionService.getCurrentCompletion

        if (currentItem != null && currentItem.isValid && completion != null) {
          docManager.showJavaDocInfo(lookup.getEditor, lookup.getPsiFile, false)
        }
      }
    }

    private def closeDocPopup(): Unit = {
      val docInfoHint = DocumentationManager.getInstance(lookup.getProject).getDocInfoHint
      if (docInfoHint != null) docInfoHint.cancel()
    }

    private def lookupClosed(): Unit = {
      alarm.cancelAllRequests()
      closeDocPopup()
      lookup.removeLookupListener(this)
    }
  }
}
