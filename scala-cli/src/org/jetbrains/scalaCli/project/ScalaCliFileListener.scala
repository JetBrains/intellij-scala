package org.jetbrains.scalaCli.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileVisitor.skipTo
import com.intellij.openapi.vfs.newvfs.events.{VFileCopyEvent, VFileCreateEvent, VFileDeleteEvent, VFileEvent}
import com.intellij.openapi.vfs.{AsyncFileListener, VfsUtilCore, VirtualFile, VirtualFileVisitor}
import org.jetbrains.bsp.{BSP, BspUtil}
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.plugins.scala.extensions.inReadAction

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SeqHasAsJava}

/**
 * A file listener responsible for scheduling a Scala CLI project reload whenever certain file operations occur.
 *
 * Key functionalities:
 *  - Monitors files and directories changes, including adding, removing, and copying files within nested directories
 *  - Supports `.scala`, `.sc`, and `.java` extensons
 *  - Handles file operations performed within IntelliJ IDEA (e.g., via the `New` button) as well as those occurring externally in the file system
 *  - Prevents unintended processing of files under the `.scala-build` directory
 *  - Triggers a project reload only for the external project containing the modified files. If multiple external projects are linked,
 *    only the affected project is reloaded
 */
class ScalaCliFileListener(project: Project) extends AsyncFileListener {

  private val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
  private val autoImportProjectTracker = AutoImportProjectTrackerCompanionProxy.autoImportProjectTracker(project)

  private val logger = Logger.getInstance(getClass)

  override def prepareChange(events: util.List[? <: VFileEvent]): AsyncFileListener.ChangeApplier = {
    val creationEvents = events.asScala.collect {
      case e: VFileCreateEvent => e
      case e: VFileCopyEvent  => e
    }

    val deletionEvents = events.asScala.collect { case e: VFileDeleteEvent => e }

    val scalaCliProjectSettings = getScalaCliProjectSettings(project)
    val createChangeApplier = (creationEvents.nonEmpty || deletionEvents.nonEmpty) && scalaCliProjectSettings.nonEmpty

    if (!createChangeApplier) return null

    new AsyncFileListener.ChangeApplier {
      private val affectedFiles = mutable.Buffer.empty[VirtualFile]

      override def beforeVfsChange(): Unit =
        affectedFiles ++= deletionEvents.flatMap(findRelevantFileInDeleteEvent)

      override def afterVfsChange(): Unit = {
        affectedFiles ++= creationEvents.flatMap(findRelevantFileInEvent)

        val externalPathsToReload = affectedFiles.flatMap { file =>
          val settings = scalaCliProjectSettings.find(isFileInExternalProject(file, _))
          settings.map(_.getExternalProjectPath)
        }.toSet

        externalPathsToReload.foreach { path =>
          autoImportProjectTracker.markDirty(new ExternalSystemProjectId(BSP.ProjectSystemId, path))
          autoImportProjectTracker.scheduleProjectRefresh()
        }
      }
    }
  }

  private def findRelevantFileInDeleteEvent(event: VFileDeleteEvent): Option[VirtualFile] = {
    def isRelevant(file: VirtualFile): Boolean = inReadAction {
      // Consider a file as a relevant deleted file only if it is in source content
      hasExpectedExtension(file) && !isUnderScalaBuildDirectory(file) && fileIndex.isInSourceContent(file)
    }

    val file = event.getFile
    if (file.isDirectory) {
      getRelevantChild(file, isRelevant)
    } else {
      Option(file).filter(isRelevant)
    }
  }

  private def findRelevantFileInEvent(event: VFileEvent): Option[VirtualFile] = {
    def isRelevant(file: VirtualFile): Boolean = inReadAction {
      hasExpectedExtension(file) && !isUnderScalaBuildDirectory(file) && fileIndex.isInContent(file) && !fileIndex.isInSourceContent(file)
    }

    val file = event.getFile
    event match {
      case e: VFileCopyEvent =>
        Option(e.findCreatedFile()).filter(isRelevant)
      case _: VFileCreateEvent if file != null && !file.isDirectory && isRelevant(file) =>
        Option(file)
      case _: VFileCreateEvent if file != null && file.isDirectory =>
        getRelevantChild(file, isRelevant)
      case _ => None
    }
  }

  private def getRelevantChild(rootFile: VirtualFile, isRelevant: VirtualFile => Boolean): Option[VirtualFile] = {
    var result: VirtualFile = null

    try {
      VfsUtilCore.visitChildrenRecursively(rootFile, new VirtualFileVisitor[Void]() {
        override def visitFileEx(file: VirtualFile): VirtualFileVisitor.Result =
          if (isRelevant(file)) {
            result = file
            skipTo(rootFile)
          } else {
            val shouldContinue = file.isDirectory && !fileIndex.isExcluded(file) && !isUnderScalaBuildDirectory(file)
            if (shouldContinue) VirtualFileVisitor.CONTINUE else VirtualFileVisitor.SKIP_CHILDREN
          }
      })
    } catch {
      case e: Exception =>
        logger.warn(s"Error while traversing directory in ${rootFile.getPath}", e)
    }

    Option(result)
  }


  private def hasExpectedExtension(file: VirtualFile): Boolean = {
    val extension = file.getExtension
    extension != null && (extension == "scala" || extension == "sc" || extension == "java")
  }

  // Once the fix for https://github.com/VirtusLab/scala-cli/issues/3644 is included in a Scala CLI release, this check should theoretically no longer be necessary,
  // as the .scala-build directory will be excluded. However, for backward compatibility, this method should remain for the time being.
  private def isUnderScalaBuildDirectory(virtualFile: VirtualFile): Boolean =
    VfsUtilCore.findContainingDirectory(virtualFile, ".scala-build") != null

  private def getScalaCliProjectSettings(project: Project): Seq[BspProjectSettings] = {
    val bspProjectSettings = BspUtil.bspSettings(project).getLinkedProjectsSettings.asScala.toSeq
    bspProjectSettings.filter { settings =>
      BspUtil.isBspScalaCliProject(project, settings.getExternalProjectPath)
    }
  }

  private def isFileInExternalProject(file: VirtualFile, projectSettings: BspProjectSettings): Boolean = {
    val path = file.getCanonicalPath
    path != null && VfsUtilCore.isUnder(path, Seq(projectSettings.getExternalProjectPath).asJava)
  }
}
