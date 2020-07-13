package org.jetbrains.plugins.scala
package project
package notification
package source

import java.util
import java.util._

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.ide.highlighter.{JavaClassFileType, JavaFileType}
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.roots.{LibraryOrderEntry, OrderEntry, ProjectRootManager}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{ActionCallback, Comparing, Key}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications, GuiUtils}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.UnloadAwareDisposable

/**
 * @author Alexander Podkhalyuzin
 */

//todo: possibly join with AttachSourcesNotificationProvider
//todo: differences only in JavaEditorFileSwapper -> ScalaEditorFileSwapper
class ScalaAttachSourcesNotificationProvider
  extends EditorNotifications.Provider[EditorNotificationPanel] {
  private val EXTENSION_POINT_NAME: ExtensionPointName[AttachSourcesProvider] =
    new ExtensionPointName[AttachSourcesProvider]("com.intellij.attachSourcesProvider")

  EXTENSION_POINT_NAME.addChangeListener(() => {
    for (project <- ProjectManager.getInstance.getOpenProjects) {
      EditorNotifications.getInstance(project).updateNotifications(this)
    }
  }, UnloadAwareDisposable.scalaPluginDisposable)

  override def getKey: Key[EditorNotificationPanel] =
    ScalaAttachSourcesNotificationProvider.KEY

  override def createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel = {
    if (file.getFileType ne JavaClassFileType.INSTANCE) return null
    val libraries: util.List[LibraryOrderEntry] = findOrderEntriesContainingFile(file, project)
    if (libraries == null) return null

    val scalaFile = PsiManager.getInstance(project).findFile(file) match {
      case scalaFile: ScalaFile => scalaFile
      case _ => return null //as Java has now different message
    }

    val fqn = ScalaEditorFileSwapper.getFQN(scalaFile)
    if (fqn == null || ScalaEditorFileSwapper.findSourceFile(project, file) != null) return null

    val panel: EditorNotificationPanel = new EditorNotificationPanel
    val sourceFile: VirtualFile = findSourceFile(file)
    var defaultAction: AttachSourcesProvider.AttachSourcesAction = null
    if (sourceFile != null) {
      panel.setText(ScalaBundle.message("library.sources.not.attached"))
      defaultAction = new AttachSourcesUtil.AttachJarAsSourcesAction(file)
    } else {
      panel.setText(ScalaBundle.message("library.sources.not.found"))
      defaultAction = new AttachSourcesUtil.ChooseAndAttachSourcesAction(project, panel)
    }



    val actions: util.List[AttachSourcesProvider.AttachSourcesAction] = new util.ArrayList[AttachSourcesProvider.AttachSourcesAction]
    var hasNonLightAction: Boolean = false
    for (each <- EXTENSION_POINT_NAME.getExtensions) {
      each.getActions(libraries, scalaFile).forEach { action =>
        if (hasNonLightAction) {
          if (!action.isInstanceOf[AttachSourcesProvider.LightAttachSourcesAction]) {
            actions.add(action)
          }
        } else {
          if (!action.isInstanceOf[AttachSourcesProvider.LightAttachSourcesAction]) {
            actions.clear()
            hasNonLightAction = true
          }
          actions.add(action)
        }
      }
    }
    Collections.sort(actions, (o1: AttachSourcesProvider.AttachSourcesAction, o2: AttachSourcesProvider.AttachSourcesAction) => {
      o1.getName.compareToIgnoreCase(o2.getName)
    })

    actions.add(defaultAction)

    val iterator = actions.iterator()
    while (iterator.hasNext) {
      val each = iterator.next()
      //noinspection ReferencePassedToNls
      panel.createActionLabel(GuiUtils.getTextWithoutMnemonicEscaping(each.getName), new Runnable {
        override def run(): Unit = {
          if (!Comparing.equal(libraries, findOrderEntriesContainingFile(file, project))) {
            Messages.showErrorDialog(project, ScalaBundle.message("cannot.find.library.for", StringUtil.getShortName(fqn)), ScalaBundle.message("cannot.find.library.error.title"))
            return
          }
          //noinspection ReferencePassedToNls
          panel.setText(each.getBusyText)
          val onFinish: Runnable = () => {
            invokeLater(panel.setText(ScalaBundle.message("library.sources.not.found")))
          }
          val callback: ActionCallback = each.perform(findOrderEntriesContainingFile(file, project))
          callback.doWhenRejected(onFinish)
          callback.doWhenDone(onFinish)
        }
      })
    }
    panel
  }

  private def findOrderEntriesContainingFile(file: VirtualFile, project: Project): util.List[LibraryOrderEntry] = {
    val libs: util.List[LibraryOrderEntry] = new util.ArrayList[LibraryOrderEntry]
    val entries: util.List[OrderEntry] = ProjectRootManager.getInstance(project).getFileIndex.getOrderEntriesForFile(file)
    entries.forEach {
      case entry: LibraryOrderEntry =>
        libs.add(entry)
      case _ =>
    }
    if (libs.isEmpty) null else libs
  }

  private def findSourceFile(classFile: VirtualFile): VirtualFile = {
    val parent: VirtualFile = classFile.getParent
    var name: String = classFile.getName
    var i: Int = name.indexOf('$')
    if (i != -1) name = name.substring(0, i)
    i = name.indexOf('.')
    if (i != -1) name = name.substring(0, i)
    parent.findChild(name + JavaFileType.DOT_DEFAULT_EXTENSION)
  }
}

object ScalaAttachSourcesNotificationProvider {
  private val KEY = Key.create[EditorNotificationPanel]("add sources to class")
}