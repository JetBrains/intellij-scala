package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.language.completion.LookupAutoDocumentationProvider._
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder

final class LookupAutoDocumentationProvider extends LookupManagerListener {
  override def activeLookupChanged(oldLookup: Lookup, newLookup: Lookup): Unit =
    if (!isHeadlessEnv && newLookup != null) newLookup.addLookupListener(lookupListener)
}

object LookupAutoDocumentationProvider {
  private def isHeadlessEnv = ApplicationManager.getApplication.isHeadlessEnvironment

  private val lookupListener = new LookupListener {
    override def currentItemChanged(event: LookupEvent): Unit =
      if (event.getItem != null && event.getItem.getPsiElement.is[SbtScalacOptionDocHolder]) {
        showDocumentation(event.getLookup)
      }

    override def itemSelected(event: LookupEvent): Unit = {
      val docInfoHint = DocumentationManager.getInstance(event.getLookup.getProject).getDocInfoHint
      // close documentation popup
      if (docInfoHint != null) docInfoHint.cancel()
    }

    private def showDocumentation(lookup: Lookup): Unit = {
      val docManager = DocumentationManager.getInstance(lookup.getProject)
      // show docs only if not shown yet
      if (docManager.getDocInfoHint == null) {
        docManager.showJavaDocInfo(lookup.getEditor, lookup.getPsiFile, false)
      }
    }
  }
}
