package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

import scala.collection.concurrent.TrieMap

/**
 * Tracks the files for which the document compiler can be used, i.e. the files whose module has already been
 * compiled by a successful incremental compilation, so that subsequent edits only need a document compilation.
 *
 */
@Service(Array(Service.Level.PROJECT))
final class DocumentCompilerAvailabilityService(project: Project) extends Disposable {

  private val available: TrieMap[VirtualFile, java.lang.Boolean] = TrieMap.empty

  def isAvailableFor(virtualFile: VirtualFile): Boolean = available.contains(virtualFile)

  /**
   * Makes the document compiler available for `virtualFile`, but only while it is the file of the selected
   * editor: the document compiler compiles what is in the editor, so warming it up for a file the user is no
   * longer looking at is pointless.
   */
  def enable(virtualFile: VirtualFile): Unit = {
    if (project.isDisposed) return
    if (!virtualFile.isValid) return
    val selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor
    if (selectedEditor eq null) return
    if (virtualFile == selectedEditor.getFile) {
      available.put(virtualFile, java.lang.Boolean.TRUE)
    }
  }

  def disable(virtualFile: VirtualFile): Unit = {
    available.remove(virtualFile, java.lang.Boolean.TRUE)
  }

  def disableAll(): Unit = {
    available.clear()
  }

  override def dispose(): Unit = disableAll()
}

object DocumentCompilerAvailabilityService {
  def apply(project: Project): DocumentCompilerAvailabilityService =
    project.getService(classOf[DocumentCompilerAvailabilityService])
}
