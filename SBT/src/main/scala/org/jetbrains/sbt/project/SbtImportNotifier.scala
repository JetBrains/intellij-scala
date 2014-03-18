package org.jetbrains.sbt
package project

import com.intellij.openapi.components.{ServiceManager, ProjectComponent}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.util.NotificationUtil
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.sbt.language.SbtFileType
import java.util.concurrent.ConcurrentHashMap
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import org.jetbrains.sbt.project.settings.{SbtProjectSettings => Settings, SbtSettings}
import java.lang.String
import java.io.File
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import java.util.Collections
import com.intellij.openapi.editor.ex.{FocusChangeListener, EditorEx}
import com.intellij.openapi.editor.Editor
import scala.Predef

/**
 * User: Dmitry Naydanov
 * Date: 11/27/13
 */
class SbtImportNotifier(private val project: Project, private val fileEditorManager: FileEditorManager) extends ProjectComponent {
  private val myMap = new ConcurrentHashMap[String, Long]()
  private val myExternalProjectPathProvider = new SbtAutoImport
  
  private var myReImportIgnored = false
  private var myNoImportIgnored = false
  
  def disposeComponent() {}

  def initComponent() {}

  def projectClosed() {}

  def projectOpened() {
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileEditorListener)
  }

  def getComponentName: String = "SBT Notifier"
  
  private def showReImportNotification(forFile: String) {
    val externalProjectPath = getExternalProject(forFile)
    val sbtSettings = getSbtSettings getOrElse { return }
    val projectSettings = sbtSettings.getLinkedProjectSettings(externalProjectPath)
    if (projectSettings == null || projectSettings.isUseAutoImport) return 
    
    def refresh() {
      FileDocumentManager.getInstance.saveAllDocuments()
      
      ExternalSystemUtil.refreshProject(project, SbtProjectSystem.Id, getExternalProject(forFile),
        SbtImportNotifier.EmptyCallback, false, ProgressExecutionMode.IN_BACKGROUND_ASYNC, true)
    }
    
    if (externalProjectPath != null) builder(SbtImportNotifier reimportMessage forFile).setTitle("Re-import project?").setHandler{
      case "reimport" => refresh()
      case "autoimport" =>
        val projectSettings = sbtSettings.getLinkedProjectSettings(externalProjectPath)
        projectSettings setUseAutoImport true
        refresh()
      case "ignore" => myReImportIgnored = true
      case _ =>
    }.show()  
  }
  
  private def checkNoImport(forFile: String) {
    val sbtSettings = getSbtSettings getOrElse { return }

    val myExternalProject = getExternalProject(forFile)
    if (myExternalProject != null && sbtSettings.getLinkedProjectSettings(myExternalProject) == null) {//todo what if project == null
      builder(SbtImportNotifier noImportMessage forFile).setTitle("Import project").setHandler {
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
        case "ignore" => myNoImportIgnored = false
        case _ =>
      }
    }.show()
  }
  
  private def getExternalProject(filePath: String) = myExternalProjectPathProvider.getAffectedExternalProjectPath(filePath, project)
  
  private def builder(message: String) = NotificationUtil.builder(project, message).setGroup("SBT")
  
  private def getSbtSettings: Option[SbtSettings] = ExternalSystemApiUtil.getSettings(project, SbtProjectSystem.Id) match {
    case sbta: SbtSettings => Some(sbta)
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
    }

    def fileOpened(source: FileEditorManager, file: VirtualFile) {
      if (!checkCanBeSbtFile(file)) return
      
      if (!myNoImportIgnored) checkNoImport(file.getCanonicalPath)
      if (!myReImportIgnored) myMap.put(file.getCanonicalPath, file.getModificationStamp)
      
      source getSelectedEditor file match {
        case txt: TextEditor => txt.getEditor match {
          case ext: EditorEx => ext addFocusListener new FocusChangeListener {
            def focusGained(editor: Editor) {
              
            }

            def focusLost(editor: Editor) {
              if (myReImportIgnored) return

              val path = file.getCanonicalPath
              val stamp = file.getModificationStamp

              val stampLong = myMap.get(path)
              if (stampLong == 0 || stampLong != stamp) {
                myMap.put(path, stamp)
                showReImportNotification(path)
              }
            }
          }
          case _ =>
        }
        case _ => 
      }
    }
  }
}

object SbtImportNotifier {
  def getInstance(project: Project) = ServiceManager.getService(project, classOf[SbtImportNotifier])

  private def getProjectName(path: String) = new File(path).getParentFile.getName
  
  private val reimportMessage =
    (fileName: String) => s"""
                            |<html>
                            |<body>
                            | Build file '$fileName' in project '${getProjectName(fileName)}' was changed. Refresh project? <br>
                            |
                            | <a href="ftp://reimport"> Refresh </a><br>    
                            | <a href="ftp://autoimport">Enable auto-import</a><br>
                            | <a href="ftp://ignore">Ignore</a>
                            | </body>
                            | </html>
                          """.stripMargin
  
  private val noImportMessage =
    (fileName: String) => s"""
                            | File '$fileName' seems to be SBT build file, but there is no external project related to it.
                            | Import the corresponding project?<br>
                            |
                            | <a href="ftp://import">Import project</a><br>
                            | <a href="ftp://ignore">Ignore</a><br>
                          """.stripMargin
  
  private object EmptyCallback extends ExternalProjectRefreshCallback {
    def onFailure(errorMessage: String, errorDetails: String) {}

    def onSuccess(externalProject: DataNode[ProjectData]) {}
  }
}