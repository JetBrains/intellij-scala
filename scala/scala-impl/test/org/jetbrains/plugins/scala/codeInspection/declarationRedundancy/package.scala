package org.jetbrains.plugins.scala.codeInspection

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.workspace.ScratchRootsEntity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.{VirtualFileUrls, WorkspaceModel}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.assertFalse

import scala.jdk.CollectionConverters.{CollectionHasAsScala, IteratorHasAsScala}

package object declarationRedundancy {

  private[declarationRedundancy] def deleteAllGlobalScratchFiles(project: Project): Unit =
    inWriteAction {
      for {
        root <- scratchRootsFromScratchFileService ++ scratchRootsFromWorkspaceModel(project)
        scratchFile <- root.getChildren
      } {
        scratchFile.delete(null)
        scratchFile.delete(null)
        assertFalse(s"Can't delete scratch file: $scratchFile", scratchFile.exists())
      }
    }

  // the old way
  private def scratchRootsFromScratchFileService: Seq[VirtualFile] =
    ScratchFileService.getAllRootPaths.asScala.toSeq

  // the new way
  private def scratchRootsFromWorkspaceModel(project: Project): Seq[VirtualFile] =
    for {
      scratchFileFolder <- scratchRootsEntities(project)
      rootUrl <- scratchFileFolder.getRoots.asScala
      root <- Option(VirtualFileUrls.getVirtualFile(rootUrl))
    } yield root

  private def scratchRootsEntities(project: Project): Seq[ScratchRootsEntity] =
    WorkspaceModel
      .getInstance(project)
      .getCurrentSnapshot
      .entities(classOf[ScratchRootsEntity])
      .iterator()
      .asScala.toSeq
}
