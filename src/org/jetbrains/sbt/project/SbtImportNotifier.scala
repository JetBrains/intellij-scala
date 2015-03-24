package org.jetbrains.sbt
package project

import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import com.intellij.notification.{Notification, NotificationDisplayType, NotificationType, NotificationsConfiguration}
import com.intellij.openapi.components.{ProjectComponent, ServiceManager}
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
class SbtImportNotifier(private val project: Project, private val fileEditorManager: FileEditorManager) extends ProjectComponent {
  private val myExternalProjectPathProvider = new SbtAutoImport
  
  private var myReImportIgnored = false
  private var myNoImportIgnored = false

  val myMap = new ConcurrentHashMap[Document, DocumentAdapter]()

  private var myNotification: Option[(Notification, String)] = None

  def disposeComponent() {}

  def initComponent() {}

  def projectClosed() {}

  def projectOpened() {
    NotificationsConfiguration.getNotificationsConfiguration.register(SbtImportNotifier.groupName,
      NotificationDisplayType.STICKY_BALLOON)
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileEditorListener)
  }

  def getComponentName: String = "SBT Notifier"

  private def expireNotification() {
    myNotification map (_._1.expire())
    myNotification = None
  }

  private def showReImportNotification(forFile: String) {
    myNotification map {
      case (n, oldFile) if oldFile == forFile => return
      case _ =>
    }

    expireNotification()

    val externalProjectPath = getExternalProject(forFile)
    if (externalProjectPath == null) return

    val sbtSettings = getSbtSettings getOrElse { return }
    val projectSettings = sbtSettings.getLinkedProjectSettings(externalProjectPath)
    if (projectSettings == null || projectSettings.isUseAutoImport) return 
    
    def refresh() {
      FileDocumentManager.getInstance.saveAllDocuments()

      ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
    }

    if (externalProjectPath == null) return

    val build = builder(SbtImportNotifier reimportMessage forFile).setTitle("Re-import project?").setHandler{
      case "reimport" =>
        refresh()
        myNotification = None
      case "autoimport" =>
        val projectSettings = sbtSettings.getLinkedProjectSettings(externalProjectPath)
        projectSettings setUseAutoImport true
        refresh()
        myNotification = None
      case "ignore" => myReImportIgnored = true
      case _ =>
    }.setNotificationType(NotificationType.INFORMATION)
    val notification = build.notification

    myNotification = Some((notification, forFile))
    build show notification
  }
  
  private def checkNoImport(forFile: String) {
    val sbtSettings = getSbtSettings getOrElse { return }

    myNotification map {
      case (n, oldFile) if oldFile == forFile => return
      case _ =>
    }

    expireNotification()

    val myExternalProject = getExternalProject(forFile)
    if (myExternalProject == null || sbtSettings.getLinkedProjectSettings(myExternalProject) != null) return

    val build = builder(SbtImportNotifier noImportMessage forFile).setTitle("Import project").setHandler {
      case "import" =>
        val projectSettings = new Settings
        projectSettings setUseAutoImport true
        projectSettings setExternalProjectPath (
          if (forFile.endsWith(".scala")) new File(forFile).getParentFile.getParent else new File(forFile).getParent
        )

        val callback = new ExternalProjectRefreshCallback() {
          def onFailure(errorMessage: String, errorDetails: String) { }

          def onSuccess(externalProject: DataNode[ProjectData]) {
            if (externalProject == null) return

            val projects = ContainerUtilRt.newHashSet(sbtSettings.getLinkedProjectsSettings)
            projects add projectSettings
            sbtSettings setLinkedProjectsSettings projects

            ExternalSystemApiUtil executeProjectChangeAction new DisposeAwareProjectChange(project) {
              def execute() {
                ProjectRootManagerEx.getInstanceEx(project) mergeRootsChangesDuring new Runnable {
                  def run() {
                    val dataManager: ProjectDataManager = ServiceManager.getService(classOf[ProjectDataManager])
                    dataManager.importData[ProjectData](externalProject.getKey, Collections.singleton(externalProject), project, false)
                  }
                }
              }
            }

          }
        }

        FileDocumentManager.getInstance.saveAllDocuments()

        ExternalSystemUtil.refreshProject(project, SbtProjectSystem.Id, projectSettings.getExternalProjectPath, callback,
          false, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
        myNotification = None
      case "ignore" => myNoImportIgnored = false
      case _ =>
    }.setNotificationType(NotificationType.INFORMATION)

    val notification = build.notification
    myNotification = Some((notification, forFile))
    build show notification
  }
  
  private def getExternalProject(filePath: String) = {
    def getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project) = {
      import com.intellij.openapi.vfs.VfsUtilCore._
      val changed = new File(changedFileOrDirPath)
      val name = changed.getName

      val base = new File(project.getBasePath)
      val build = base / Sbt.ProjectDirectory

      (name.endsWith(s".${Sbt.FileExtension}") && isAncestor(base, changed, true) ||
              name.endsWith(".scala") && isAncestor(build, changed, true))
              .option(base.canonicalPath).orNull
    }

    if (project.isDisposed) null else getAffectedExternalProjectPath(filePath, project)
  }

  private def builder(message: String) = NotificationUtil.builder(project, message).setGroup(SbtImportNotifier.groupName)
  
  private def getSbtSettings: Option[SbtSystemSettings] = ExternalSystemApiUtil.getSettings(project, SbtProjectSystem.Id) match {
    case sbta: SbtSystemSettings => Some(sbta)
    case _ => None 
  }
  
  private def checkCanBeSbtFile(file: VirtualFile): Boolean = {
    val name = file.getName

    if (name == "plugins.sbt" || name == null) return false //process only build.sbt and Build.scala
    if (file.getFileType == SbtFileType) return true
    if (name != "Build.scala") return false
    
    val parent = file.getParent
    parent != null && parent.getName == "project"
  }

  object MyFileEditorListener extends FileEditorManagerListener {
    def selectionChanged(event: FileEditorManagerEvent) {}

    def fileClosed(source: FileEditorManager, file: VirtualFile) {
      val project = source.getProject

      val path = file.getCanonicalPath

      if (myNotification.exists {
        case (_, p) if p == path => true
        case _ => false
      }) expireNotification()

      if (!file.isValid) return
      val psiFile = PsiManager.getInstance(project).findFile(file)
      if (psiFile == null) return

      val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
      if (document == null) return

      val listener = myMap.remove(document)
      if (listener != null) {
        document.removeDocumentListener(listener)
      }
    }

    def fileOpened(source: FileEditorManager, file: VirtualFile) {
      if (!file.isValid) return
      if (!checkCanBeSbtFile(file)) return
      
      if (!myNoImportIgnored) checkNoImport(file.getCanonicalPath)

      source getSelectedEditor file match {
        case txt: TextEditor => txt.getEditor match {
          case ext: EditorEx =>
            if (myReImportIgnored) return

            val path = file.getCanonicalPath

            val listener: DocumentAdapter = new DocumentAdapter {
              override def documentChanged(e: DocumentEvent) {
                if (myNotification exists {
                  case (_, p) => p == path
                }) return

                showReImportNotification(path)
              }
            }

            val document = ext.getDocument
            document.addDocumentListener(listener)
            myMap.put(document, listener)
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