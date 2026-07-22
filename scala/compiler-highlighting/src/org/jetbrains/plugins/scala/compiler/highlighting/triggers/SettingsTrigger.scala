package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.{ExternalHighlightersService, HighlightingTriggerService}
import org.jetbrains.plugins.scala.extensions.{inReadAction, invokeAndWait}

private[highlighting] object SettingsTrigger:
  private val logger = Logger.getInstance(classOf[this.type])
  def trigger(project: Project, root: PsiElement, setting: FileHighlightingSetting): Unit =
    if !root.getLanguage.isKindOf(ScalaLanguage.INSTANCE) then return

    val psiFile = root.getContainingFile
    if psiFile eq null then return

    val virtualFile = psiFile.getVirtualFile
    if virtualFile eq null then return

    val debugReason = s"FileHighlightingSetting changed for ${virtualFile.getCanonicalPath}"
    HighlightingTriggerService(project).bypass(virtualFile, debugReason)

    // Wipe the existing visual highlighters and problem views asynchronously
    cleanHighlightings(project, virtualFile, debugReason)
          
  private inline def cleanHighlightings(project:Project, virtualFile:VirtualFile, debugReason: String): Unit =
    executeOnBackgroundThreadInNotDisposed(project):
      if virtualFile.isValid then
        val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
        if document ne null then
          invokeAndWait:
            EditorFactory.getInstance().getEditors(document).foreach: editor =>
              ExternalHighlightersService(project).eraseHighlightings(editor)
  