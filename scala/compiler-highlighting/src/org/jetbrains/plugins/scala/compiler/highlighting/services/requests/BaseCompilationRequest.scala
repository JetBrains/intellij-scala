package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.editor.{Document, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.util.DocumentUtil
import org.jetbrains.plugins.scala.util.DocumentVersion

import scala.concurrent.duration.Deadline

abstract class BaseCompilationRequest(
  final val originFiles: Map[VirtualFile, Document],
  val deadline: Deadline,
  val id: RequestId
) extends CompilationRequest {


  override final val documentVersions: Map[VirtualFile, DocumentVersion] =
    originFiles.map { case (vf, doc) => vf -> DocumentUtil.documentVersion(vf, doc) }

   protected def isExpired: Boolean = {
    originFiles.keys.exists(!_.isValid) ||
      documentVersions.exists { case (virtualFile, version) =>
        val document = originFiles(virtualFile)
        version.version != DocumentUtil.version(document)
      }
  }
  protected def canDocumentBeCompiled(project: Project, document: Document): RequestState = {
    val editors = EditorFactory.getInstance().getEditors(document, project)
    if (editors.isEmpty) {
      // There are no open editors for the document. Skip the request.
      RequestState.Expired
    } else {
      // If there are no active lookups or templates in the editor, it can be compiled.
      // Otherwise, delay the request, in case the user dismisses lookups and templates without typing letters
      // (e.g. with the Esc key)
      val ready = editors.exists { editor =>
        LookupManager.getActiveLookup(editor) == null &&
          TemplateManager.getInstance(project).getActiveTemplate(editor) == null
      }
      if (ready) RequestState.Ready else RequestState.NotReady
    }
  }
}