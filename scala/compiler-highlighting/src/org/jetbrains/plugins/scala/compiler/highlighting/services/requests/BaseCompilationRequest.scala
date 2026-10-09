package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.util.DocumentUtil
import org.jetbrains.plugins.scala.util.DocumentVersion

abstract class BaseCompilationRequest(
  project: Project,
  val originFiles: Map[VirtualFile, Document],
  val id: RequestId
) extends CompilationRequest:

  protected val Log: Logger = Logger.getInstance(classOf[CompilationRequest])
  
  override final val documentVersions: Map[VirtualFile, DocumentVersion] =
    originFiles.map { case (vf, doc) => vf -> DocumentUtil.documentVersion(vf, doc) }

  override def isReadyForExecution: RequestState =
    if (isExpired) return RequestState.Expired
    if (DumbService.isDumb(project)) return RequestState.NotReady
      canDocumentsBeCompiled
  
  /**
   * A scope is worth compiling as long as one of its files can show the result.
   *
   * A file with no editor is expired on its own terms, but here it is one this compilation exists to build:
   * the scope covers everything unbuilt, and something unbuilt is very often something closed. Expiring the
   * whole request for it would drop the compilation that was supposed to build it, leaving it unbuilt and
   * every later scope containing it — and expiring for the same reason.
   */
  private def canDocumentsBeCompiled: RequestState = 
    val states = originFiles.valuesIterator.map(canDocumentBeCompiled(project, _)).toSet
    if (states.forall(_ == RequestState.Expired)) RequestState.Expired
    else if (states.contains(RequestState.NotReady)) RequestState.NotReady
    else RequestState.Ready
  
  private def isExpired: Boolean =
    originFiles.keys.exists(!_.isValid) ||
      documentVersions.exists { case (virtualFile, version) =>
        val document = originFiles(virtualFile)
        version.version != DocumentUtil.version(document)
      }
      
  protected def canDocumentBeCompiled(project: Project, document: Document): RequestState =
    RequestState.of(project, document)