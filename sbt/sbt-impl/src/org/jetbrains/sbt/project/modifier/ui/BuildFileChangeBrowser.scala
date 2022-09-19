package org.jetbrains.sbt.project.modifier.ui

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.openapi.vfs.VirtualFile

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.{ListHasAsScala, SeqHasAsJava}

class BuildFileChangeBrowser(val project: Project,
                             val changes: java.util.List[Change],
                             val fileChangesMap: mutable.Map[VirtualFile, (BuildFileModifiedStatus, Long)])
  extends SimpleChangesBrowser(project, changes) {

  override def setChangesToDisplay(changes: util.Collection[_ <: Change]): Unit = {
    val updatedChanges = new java.util.ArrayList[Change]
    updatedChanges.addAll(
      getSelectedChanges.asScala.map {
        myChange => {
          val changeSwapped = BuildFileChange.swap(myChange.asInstanceOf[BuildFileChange])
          fileChangesMap.get(changeSwapped.getVirtualFile).map {
            case (modifiedStatus, modificationStamp) =>
              val newModificationStamp = FileDocumentManager.getInstance().getDocument(changeSwapped.getVirtualFile)
                .getModificationStamp

              if (newModificationStamp != modificationStamp) {
                val newStatus = modifiedStatus.changeAfterManualModification()
                fileChangesMap.put(changeSwapped.getVirtualFile, (newStatus, newModificationStamp))
                BuildFileChange.swap(new BuildFileChange(changeSwapped.getBeforeRevision, changeSwapped.getAfterRevision, newStatus))
              } else myChange
            case _ => myChange
          }.getOrElse(myChange)
        }
      }.asJava
    )
    super.setChangesToDisplay(changes)
  }
}