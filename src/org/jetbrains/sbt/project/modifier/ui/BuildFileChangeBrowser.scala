package org.jetbrains.sbt.project.modifier.ui

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.{ShowDiffAction, ShowDiffUIContext}
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser
import com.intellij.openapi.vfs.VirtualFile
import scala.collection.JavaConversions._
import scala.collection.mutable

/**
 * @author Roman.Shein
 * @since 20.03.2015.
 */
class BuildFileChangeBrowser(val project: Project, val changes: java.util.List[Change], val canExcludeChanges: Boolean,
                             val fileChangesMap: mutable.Map[VirtualFile, (BuildFileModifiedStatus, Long)]) extends
ChangesBrowser(project, null, changes, null, canExcludeChanges, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES,
  null) {

  override def afterDiffRefresh() {
    val updatedChanges = new java.util.ArrayList[Change]
    updatedChanges.addAll(
      getSelectedChanges map {
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
      }
    )

    setChangesToDisplay(updatedChanges)
  }

  override protected def showDiffForChanges(changesArray: Array[Change], indexInSelection: Int) {
    val context: ShowDiffUIContext = new ShowDiffUIContext(false)
    val changesArraySwapped: Array[Change] = for (change <- changesArray)
    yield BuildFileChange.swap(change.asInstanceOf[BuildFileChange])

    ShowDiffAction.showDiffForChange(changesArraySwapped, indexInSelection, myProject, context)
  }
}