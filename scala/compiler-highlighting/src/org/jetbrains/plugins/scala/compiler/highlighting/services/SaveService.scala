package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.configurationStore.StoreUtilKt
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{DocumentSaveFailPhaseEvent, DocumentSavePhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.sbt.project.CoroutineAppScopeService.coroutineScope

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.BuildersKt
import scala.util.control.NonFatal

/**
 * Persists everything that must be on disk before a compilation: the unsaved documents (see
 * [[saveDocuments]]) and the project settings (see [[saveProject]]).
 */
@Service(Array(Service.Level.PROJECT))
final class SaveService(project: Project) {

  private val tracer = Tracing(project)

  private val projectSaveTracker: AtomicBoolean = new AtomicBoolean(false)

  /**
   * After enabling, the next call to [[saveProject]] will save the project
   */
  def enableProjectSave(): Unit = projectSaveTracker.set(true)

  /**
   * Saves the project settings, but only if [[enableProjectSave]] was called before; saving then disables it again, so
   * each [[enableProjectSave]] results in at most one save (see SCL-17295, SCL-22491).
   */
  def saveProject(): Unit = {
    if (projectSaveTracker.compareAndSet(true, false)) {
      if (!project.isDisposed || project.isDefault) {
        BuildersKt.runBlocking(
          coroutineScope.getCoroutineContext,
          (_, continuation) => StoreUtilKt.saveSettings(project, false, continuation)
        )
      }
    }
  }

  /**
   * Saves every unsaved document that has a PSI file. Failing to save one document is recorded on the trace
   * and does not abort the rest of the save. This function ignores the [[enableProjectSave()]] and always
   * saves the documents on each call.
   */
  @RequiresEdt
  def saveDocuments(requestId: RequestId): Unit = {
    val span = tracer.begin(DocumentSavePhaseEvent(requestId))
    val fileDocumentManager = FileDocumentManager.getInstance()
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    def name(document: Document): String =
      Option(fileDocumentManager.getFile(document)).fold(document.toString)(_.getName)
    val toSave = fileDocumentManager.getUnsavedDocuments.filter(psiDocumentManager.getPsiFile(_) ne null)
    val requested = toSave.map(name).toSet
    var saved = Set.empty[String]
    toSave.foreach { document =>
      val fileName = name(document)
      try {
        fileDocumentManager.saveDocumentAsIs(document)
        saved += fileName
      }
      catch {
        case NonFatal(_) => tracer.mark(span, DocumentSaveFailPhaseEvent(requestId, fileName))
      }
    }
    tracer.mapAndEnd(span)(_ => Some(DocumentSavePhaseEvent(requestId, requested, saved)))
  }
}

object SaveService {
  def apply(project: Project): SaveService =
    project.getService(classOf[SaveService])
}
