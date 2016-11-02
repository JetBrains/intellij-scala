package org.jetbrains.plugins.scala
package project.notification.source

import java.util
import java.util._

import com.intellij.codeEditor.JavaEditorFileSwapper
import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.daemon.impl.AttachSourcesNotificationProvider
import com.intellij.ide.highlighter.{JavaClassFileType, JavaFileType}
import com.intellij.openapi.extensions.{ExtensionPointName, Extensions}
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{LibraryOrderEntry, OrderEntry, ProjectRootManager}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{ActionCallback, Comparing}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiClass, PsiFile, PsiJavaFile, PsiManager}
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications, GuiUtils}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author Alexander Podkhalyuzin
 */

//todo: possibly join with AttachSourcesNorificationProvider
//todo: differences only in JavaEditorFileSwapper -> ScalaEditorFileSwapper
class ScalaAttachSourcesNotificationProvider(myProject: Project, notifications: EditorNotifications)
  extends AttachSourcesNotificationProvider(myProject, notifications) {
  private val EXTENSION_POINT_NAME: ExtensionPointName[AttachSourcesProvider] =
    new ExtensionPointName[AttachSourcesProvider]("com.intellij.attachSourcesProvider")

  override def createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel = {
    if (file.getFileType ne JavaClassFileType.INSTANCE) return null
    val libraries: util.List[LibraryOrderEntry] = findOrderEntriesContainingFile(file)
    if (libraries == null) return null
    val psiFile: PsiFile = PsiManager.getInstance(myProject).findFile(file)
    val isScala = psiFile.isInstanceOf[ScalaFile]
    if (!isScala) return super.createNotificationPanel(file, fileEditor) //as Java has now different message
    val fqn: String =
      if (isScala) ScalaEditorFileSwapper.getFQN(psiFile)
      else getFQN(psiFile)
    if (fqn == null) return null
    if (isScala && ScalaEditorFileSwapper.findSourceFile(myProject, file) != null) return null
    if (!isScala && JavaEditorFileSwapper.findSourceFile(myProject, file) != null) return null
    val panel: EditorNotificationPanel = new EditorNotificationPanel
    val sourceFile: VirtualFile = findSourceFile(file)
    var defaultAction: AttachSourcesProvider.AttachSourcesAction = null
    if (sourceFile != null) {
      panel.setText(ScalaBundle.message("library.sources.not.attached"))
      defaultAction = new AttachSourcesUtil.AttachJarAsSourcesAction(file, sourceFile, myProject)
    } else {
      panel.setText(ScalaBundle.message("library.sources.not.found"))
      defaultAction = new AttachSourcesUtil.ChooseAndAttachSourcesAction(myProject, panel)
    }



    val actions: util.List[AttachSourcesProvider.AttachSourcesAction] = new util.ArrayList[AttachSourcesProvider.AttachSourcesAction]
    var hasNonLightAction: Boolean = false
    for (each <- Extensions.getExtensions(EXTENSION_POINT_NAME)) {
      import scala.collection.JavaConversions._
      for (action <- each.getActions(libraries, psiFile)) {
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
    Collections.sort(actions, new Comparator[AttachSourcesProvider.AttachSourcesAction] {
      def compare(o1: AttachSourcesProvider.AttachSourcesAction, o2: AttachSourcesProvider.AttachSourcesAction): Int = {
        o1.getName.compareToIgnoreCase(o2.getName)
      }
    })

    actions.add(defaultAction)

    val iterator = actions.iterator()
    while (iterator.hasNext) {
      val each = iterator.next()
      panel.createActionLabel(GuiUtils.getTextWithoutMnemonicEscaping(each.getName), new Runnable {
        def run() {
          if (!Comparing.equal(libraries, findOrderEntriesContainingFile(file))) {
            Messages.showErrorDialog(myProject, "Cannot find library for " + StringUtil.getShortName(fqn), "Error")
            return
          }
          panel.setText(each.getBusyText)
          val onFinish: Runnable = new Runnable {
            def run() {
              invokeLater(panel.setText(ScalaBundle.message("library.sources.not.found")))
            }
          }
          val callback: ActionCallback = each.perform(findOrderEntriesContainingFile(file))
          callback.doWhenRejected(onFinish)
          callback.doWhenDone(onFinish)
        }
      })
    }
    panel
  }

  private def findOrderEntriesContainingFile(file: VirtualFile): util.List[LibraryOrderEntry] = {
    val libs: util.List[LibraryOrderEntry] = new util.ArrayList[LibraryOrderEntry]
    val entries: util.List[OrderEntry] = ProjectRootManager.getInstance(myProject).getFileIndex.getOrderEntriesForFile(file)
    import scala.collection.JavaConversions._
    for (entry <- entries) {
      entry match {
        case entry: LibraryOrderEntry =>
          libs.add(entry)
        case _ =>
      }
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

  private def getFQN(psiFile: PsiFile): String = {
    if (!psiFile.isInstanceOf[PsiJavaFile]) return null
    val classes: Array[PsiClass] = psiFile.asInstanceOf[PsiJavaFile].getClasses
    if (classes.length == 0) return null
    classes(0).getQualifiedName
  }
}