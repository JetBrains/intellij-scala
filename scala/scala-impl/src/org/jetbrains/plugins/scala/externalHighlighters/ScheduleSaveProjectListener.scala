package org.jetbrains.plugins.scala.externalHighlighters

import java.util

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.SaveAndSyncHandler.SaveTask
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.{VFileContentChangeEvent, VFileCreateEvent, VFileDeleteEvent, VFileEvent, VFileMoveEvent}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode

import scala.collection.JavaConverters._

/**
 * TODO Not sure this works correctly
 */
class ScheduleSaveProjectListener(project: Project)
  extends BulkFileListener {

  import ScheduleSaveProjectListener.acceptableExtensions

  override def after(events: util.List[_ <: VFileEvent]): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      val index = ProjectFileIndex.getInstance(project)
      val sourcesChanged = events.asScala
        .collect {
          case e: VFileContentChangeEvent => e
          case e: VFileCreateEvent => e
          case e: VFileDeleteEvent => e
          case e: VFileMoveEvent => e
        }
        .flatMap { event =>
          Option(event.getFile)
        }
        .exists { file =>
          (acceptableExtensions contains file.getExtension) &&
            belongToProject(file, index) &&
            notSelected(file)
        }
      if (sourcesChanged)
        SaveAndSyncHandler.getInstance.scheduleSave(new SaveTask(project), true)
    }

  private def notSelected(virtualFile: VirtualFile): Boolean = {
    val result = for {
      editor <- Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
      selectedFile <- Option(FileDocumentManager.getInstance.getFile(editor.getDocument))
    } yield virtualFile != selectedFile
    result.getOrElse(false)
  }

  private def belongToProject(virtualFile: VirtualFile, index: ProjectFileIndex): Boolean =
    Option(index.getModuleForFile(virtualFile)).exists { module =>
      module.getProject.getBasePath == project.getBasePath
    }
}

object ScheduleSaveProjectListener {

  // TODO more extensions?
  private val acceptableExtensions: Set[String] = Set(
    ScalaFileType.INSTANCE.getDefaultExtension,
    JavaFileType.INSTANCE.getDefaultExtension
  )
}
