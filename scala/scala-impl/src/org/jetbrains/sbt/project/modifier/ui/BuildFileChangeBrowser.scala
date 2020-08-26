package org.jetbrains.sbt.project.modifier.ui

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.{ShowDiffAction, ShowDiffContext}
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser
import com.intellij.openapi.vfs.VirtualFile
import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * @author Roman.Shein
 * @since 20.03.2015.
 */
class BuildFileChangeBrowser(val project: Project, val changes: java.util.List[Change], val canExcludeChanges: Boolean,
                             val fileChangesMap: mutable.Map[VirtualFile, (BuildFileModifiedStatus, Long)]) extends
ChangesBrowser(project, null, changes, null, canExcludeChanges, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES,
  null) {

  override def afterDiffRefresh(): Unit = {
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

    setChangesToDisplay(updatedChanges)
  }

  override protected def showDiffForChanges(changesArray: Array[Change], indexInSelection: Int): Unit = {
    val context: ShowDiffContext = new ShowDiffContext()
    val changesArraySwapped: Array[Change] = for (change <- changesArray)
    yield BuildFileChange.swap(change.asInstanceOf[BuildFileChange])

    ShowDiffAction.showDiffForChange(myProject, changesArraySwapped.toSeq.asJava, indexInSelection, context)
  }
}