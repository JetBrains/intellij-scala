package org.jetbrains.sbt.project.modifier

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{Module => IJModule}
import com.intellij.openapi.project.{Project => IJProject}
import com.intellij.openapi.vcs.changes.{CurrentContentRevision, SimpleContentRevision}
import com.intellij.openapi.vfs.{VfsUtil, VfsUtilCore, VirtualFile}
import com.intellij.testFramework.LightVirtualFile
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.modifier.ui.{BuildFileChange, BuildFileModifiedStatus, ChangesConfirmationDialog}

import scala.collection.mutable

/**
 * Provides means to modify build files associated with IJ modules. Should be used for introducing/eliminating
 * dependencies, modifying sbt build options and so on.
 */
trait BuildFileModifier {
  /**
   * Performs some specific modification(s) of sbt build file(s) (library dependencies, resolvers, sbt options, etc.)
   * @param module - module within IJ project to modify build file(s) for
   */
  protected def modifyInner(module: IJModule, vfsFileToCopy: mutable.Map[VirtualFile, LightVirtualFile]):
  Option[List[VirtualFile]]

  /**
   * Performs modification(s) of sbt build file(s) associated with the module and refreshes external system.
   * @param module - module within IJ project to modify build file(s) for
   */
  def modify(module: IJModule, needPreviewChanges: Boolean): Boolean = {

    var res = false
    val project = module.getProject
    val vfsFileToCopy = mutable.Map[VirtualFile, LightVirtualFile]()
    CommandProcessor.getInstance.executeCommand(project, () => {
      modifyInner(module, vfsFileToCopy) match {
        case Some(changes) =>
          if (!needPreviewChanges) {
            applyChanges(changes, vfsFileToCopy)
            res = true
          } else {
            previewChanges(module.getProject, changes, vfsFileToCopy) match {
              case Some(acceptedChanges) if acceptedChanges.nonEmpty =>
                applyChanges(acceptedChanges, vfsFileToCopy)
                res = true
              case _ =>
                res = false
            }
          }
        case None =>
          res = false
      }
    }, SbtBundle.message("sbt.build.file.modification"), this)
    if (res)
      ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
    res
  }

  def previewChanges(project: IJProject, changes: List[VirtualFile], filesToWorkingCopies: mutable.Map[VirtualFile,
      LightVirtualFile]): Option[List[VirtualFile]] = {
    //first, create changes and set their initial status
    val fileStatusMap = mutable.Map[VirtualFile, (BuildFileModifiedStatus, Long)]()
    val documentManager = FileDocumentManager.getInstance()
    val vcsChanges = filesToWorkingCopies.toSeq.map{case (original, copy) =>
      val originalRevision = new SimpleContentRevision(VfsUtilCore.loadText(original), VcsUtil getFilePath original, "original")
      val copyRevision: CurrentContentRevision = new CurrentContentRevision(VcsUtil getFilePath copy) {
        override def getVirtualFile: LightVirtualFile = copy
      }
      val isModified = changes.contains(copy)
      assert(!fileStatusMap.contains(copy))
      val buildFileStatus = if (isModified) BuildFileModifiedStatus.MODIFIED_AUTOMATICALLY else BuildFileModifiedStatus.DETECTED
      val buildFileModificationStamp = documentManager.getDocument(copy).getModificationStamp
      fileStatusMap.put(copy, (buildFileStatus, buildFileModificationStamp))
      new BuildFileChange(originalRevision, copyRevision, buildFileStatus)
    }
    val changesToWorkingCopies = (vcsChanges zip changes).toMap
    val dialog = ChangesConfirmationDialog(project, vcsChanges.toList, fileStatusMap)
    dialog.setModal(true)
    val isOk = dialog.showAndGet()
    if (isOk) {
      val selectedChanges = dialog.selectedChanges
      Some(for (change <- selectedChanges) yield changesToWorkingCopies(change.asInstanceOf[BuildFileChange]))
    } else None
  }

  private def applyChanges(changes: List[VirtualFile], vfsFileToCopy: mutable.Map[VirtualFile,
      LightVirtualFile]): Unit = {
    val manager = FileDocumentManager.getInstance()
    for ((originalFile, changedFile) <- vfsFileToCopy) {
      if (changes.contains(changedFile)) {
        //we only want to rewrite files that actually changed
        val changedDocument = manager.getDocument(changedFile)
          VfsUtil.saveText(originalFile, changedDocument.getText)
      }
    }
  }
}
