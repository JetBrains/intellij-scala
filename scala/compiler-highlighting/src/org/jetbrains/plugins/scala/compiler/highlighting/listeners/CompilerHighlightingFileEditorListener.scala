package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerEvent, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.EditorTrigger

/**
 * Starts compiler highlighting for the file the user has just landed in.
 *
 * Both callbacks are needed and neither implies the other: a file can be opened into a split without
 * becoming the selection, and switching tabs selects a file that was opened long ago.
 */
private final class CompilerHighlightingFileEditorListener(project: Project) extends FileEditorManagerListener {

  private val logger  = Logger.getInstance(classOf[CompilerHighlightingFileEditorListener])
  override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit = {
    activated(file, "editor opened")
  }

  override def selectionChanged(event: FileEditorManagerEvent): Unit = {
    Option(event.getNewFile).foreach(activated(_, "editor selected"))
  }

  private def activated(file: VirtualFile, source: String): Unit = {
    EditorTrigger.triggerOnFile(project, file, source)
  }
}
