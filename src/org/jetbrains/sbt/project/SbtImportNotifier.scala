package org.jetbrains.sbt
package project

import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import com.intellij.notification.{Notification, NotificationDisplayType, NotificationType, NotificationsConfiguration}
import com.intellij.openapi.components.{AbstractProjectComponent, ProjectComponent, ServiceManager}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.{DocumentAdapter, DocumentEvent}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import com.intellij.util.containers.ContainerUtilRt
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.sbt.language.SbtFileType
import org.jetbrains.sbt.project.settings.{SbtProjectSettings => Settings}
import org.jetbrains.sbt.settings.SbtSystemSettings

/**
 * User: Dmitry Naydanov
 * Date: 11/27/13
 */
class SbtImportNotifier(private val project: Project, private val fileEditorManager: FileEditorManager)
        extends AbstractProjectComponent(project) {
  private var shouldIgnoreReImport = false
  private var shouldIgnoreNoImport = false

  val listenedDocuments = new ConcurrentHashMap[Document, DocumentAdapter]()

  private var currentNotification: Option[(Notification, String)] = None

  override def projectOpened(): Unit = {
    NotificationsConfiguration.getNotificationsConfiguration.register(SbtImportNotifier.groupName,
      NotificationDisplayType.STICKY_BALLOON)
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileEditorListener)
  }

  private def expireNotification(): Unit = {
    currentNotification map (_._1.expire())
    currentNotification = None
  }

  private def isNotificationShownFor(forFile: String): Boolean =
    currentNotification.fold(false){ case (_, oldFile) => oldFile == forFile }


  private def doRefreshProject() {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }

  private def reImportNotificationHandler(projectSettings: Settings)(msg: String): Unit = msg match {
    case "reimport" =>
      doRefreshProject()
      currentNotification = None
    case "autoimport" =>
      projectSettings.setUseOurOwnAutoImport(true)
      doRefreshProject()
      currentNotification = None
    case "ignore" =>
      shouldIgnoreReImport = true
    case _ =>
  }

  private def showReImportNotification(forFile: String): Unit = {
    if (isNotificationShownFor(forFile))
      return
    expireNotification()

    for {
      externalProjectPath <- getExternalProject(forFile)
      sbtSettings <- Option(SbtSystemSettings.getInstance(project))
      projectSettings <- Option(sbtSettings.getLinkedProjectSettings(externalProjectPath))
      if !projectSettings.useOurOwnAutoImport
    } {
      val build = builder(SbtImportNotifier reimportMessage forFile)
              .setTitle("Re-import project?")
              .setNotificationType(NotificationType.INFORMATION)
              .setHandler(reImportNotificationHandler(projectSettings))
      val notification = build.notification
      currentNotification = Some((notification, forFile))
      build.show(notification)
    }
  }

  private def noImportNotificationHandler(forFile: String,
                                          sbtSystemSettings: SbtSystemSettings)
                                         (msg: String): Unit = msg match {
    case "import" =>
      val projectSettings = new Settings
      val externalProjectPath =
        if (forFile.endsWith(".scala"))
          new File(forFile).getParentFile.getParent
        else
          new File(forFile).getParent
      projectSettings.setUseOurOwnAutoImport(true)
      projectSettings.setExternalProjectPath(externalProjectPath)

      val callback = new ExternalProjectRefreshCallback() {
        def onFailure(errorMessage: String, errorDetails: String) {}

        def onSuccess(externalProject: DataNode[ProjectData]) {
          if (externalProject == null) return

          val projects = ContainerUtilRt.newHashSet(sbtSystemSettings.getLinkedProjectsSettings)
          projects.add(projectSettings)
          sbtSystemSettings.setLinkedProjectsSettings(projects)

          ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
            def execute() {
              ProjectRootManagerEx.getInstanceEx(project) mergeRootsChangesDuring new Runnable {
                def run() {
                  val dataManager: ProjectDataManager = ServiceManager.getService(classOf[ProjectDataManager])
                  dataManager.importData[ProjectData](externalProject.getKey, Collections.singleton(externalProject), project, false)
                }
              }
            }
          })
        }
      }

      FileDocumentManager.getInstance.saveAllDocuments()
      ExternalSystemUtil.refreshProject(project,
        SbtProjectSystem.Id, projectSettings.getExternalProjectPath, callback,
        false, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      currentNotification = None
    case "ignore" =>
      shouldIgnoreNoImport = true
    case _ =>
  }
  
  private def checkNoImport(forFile: String): Unit = {
    if (isNotificationShownFor(forFile))
      return
    expireNotification()

    for {
      sbtSettings <- Option(SbtSystemSettings.getInstance(project))
      myExternalProject <- getExternalProject(forFile)
      if sbtSettings.getLinkedProjectSettings(myExternalProject) == null
    } {
      val build = builder(SbtImportNotifier noImportMessage forFile)
              .setTitle("Import project")
              .setNotificationType(NotificationType.INFORMATION)
              .setHandler(noImportNotificationHandler(forFile, sbtSettings))

      val notification = build.notification
      currentNotification = Some((notification, forFile))
      build show notification
    }
  }
  
  private def getExternalProject(filePath: String): Option[String] = (!project.isDisposed).option {
      import com.intellij.openapi.vfs.VfsUtilCore._
      val changed = new File(filePath)
      val name = changed.getName

      val base = new File(project.getBasePath)
      val build = base / Sbt.ProjectDirectory

      (name.endsWith(s".${Sbt.FileExtension}") && isAncestor(base, changed, true) ||
              name.endsWith(".scala") && isAncestor(build, changed, true))
              .option(base.canonicalPath).orNull
  }

  private def builder(message: String) = NotificationUtil.builder(project, message).setGroup(SbtImportNotifier.groupName)
  
  private def checkCanBeSbtFile(file: VirtualFile): Boolean = {
    val name = file.getName

    if (name == "plugins.sbt" || name == null) return false //process only build.sbt and Build.scala
    if (file.getFileType == SbtFileType) return true
    if (name != "Build.scala") return false
    
    val parent = file.getParent
    parent != null && parent.getName == "project"
  }

  object MyFileEditorListener extends FileEditorManagerAdapter {
    override def fileClosed(source: FileEditorManager, file: VirtualFile) {
      if (isNotificationShownFor(file.getCanonicalPath))
        expireNotification()

      if (!file.isValid)
        return

      val project = source.getProject
      for {
        psiFile  <- Option(PsiManager.getInstance(project).findFile(file))
        document <- Option(PsiDocumentManager.getInstance(project)).safeMap(_.getDocument(psiFile))
        listener <- Option(listenedDocuments.remove(document))
      } {
        document.removeDocumentListener(listener)
      }
    }

    override def fileOpened(source: FileEditorManager, file: VirtualFile) {
      if (!file.isValid || !checkCanBeSbtFile(file))
        return
      if (!shouldIgnoreNoImport)
        checkNoImport(file.getCanonicalPath)

      source getSelectedEditor file match {
        case txt: TextEditor => txt.getEditor match {
          case ext: EditorEx if !shouldIgnoreReImport =>
            val path = file.getCanonicalPath
            val listener: DocumentAdapter = new DocumentAdapter {
              override def documentChanged(e: DocumentEvent) {
                if (isNotificationShownFor(path))
                  return
                showReImportNotification(path)
              }
            }
            val document = ext.getDocument
            document.addDocumentListener(listener)
            listenedDocuments.put(document, listener)
          case _ =>
        }
        case _ => 
      }
    }
  }
}

object SbtImportNotifier {

  private def getProjectName(path: String) = new File(path).getParentFile.getName

  private val groupName = "SbtImportMessage"
  
  private val reimportMessage =
    (fileName: String) => s"""
                            |<html>
                            |<body>
                            | Build file '$fileName' in project '${getProjectName(fileName)}' was changed. Refresh project? <br>
                            |
                            | <a href="ftp://reimport">Refresh</a> &nbsp;
                            | <a href="ftp://autoimport">Enable auto-import</a> &nbsp;
                            | <a href="ftp://ignore">Ignore</a>
                            | </body>
                            | </html>
                          """.stripMargin
  
  private val noImportMessage =
    (fileName: String) => s"""
                            | File '$fileName' seems to be SBT build file, but there is no external project related to it.
                            | Import the corresponding project?<br>
                            |
                            | <a href="ftp://import">Import project</a> &nbsp;
                            | <a href="ftp://ignore">Ignore</a><br>
                          """.stripMargin
  
}