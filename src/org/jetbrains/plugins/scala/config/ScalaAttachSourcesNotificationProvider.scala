package org.jetbrains.plugins.scala.config

import com.intellij.codeInsight.daemon.impl.AttachSourcesNotificationProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiManager, PsiFile}
import com.intellij.codeEditor.JavaEditorFileSwapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import javax.swing.SwingUtilities
import com.intellij.openapi.util.{ActionCallback, Comparing}
import com.intellij.openapi.roots.{OrderEntry, ProjectRootManager, LibraryOrderEntry}
import com.intellij.openapi.project.{Project, ProjectBundle}
import com.intellij.ui.{EditorNotifications, GuiUtils, EditorNotificationPanel}
import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.ide.highlighter.{JavaFileType, JavaClassFileType}
import java.util.{TreeSet, Comparator, ArrayList, List}
import com.intellij.openapi.extensions.{ExtensionPointName, Extensions}
import org.jetbrains.idea.maven.utils.MavenAttachSourcesProvider
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

  override def createNotificationPanel(file: VirtualFile): EditorNotificationPanel = {
    if (file.getFileType ne JavaClassFileType.INSTANCE) return null
    val libraries: List[LibraryOrderEntry] = findOrderEntriesContainingFile(file)
    if (libraries == null) return null
    val psiFile: PsiFile = PsiManager.getInstance(myProject).findFile(file)
    val isScala = psiFile.isInstanceOf[ScalaFile]
    val fqn: String =
      if (isScala) ScalaEditorFileSwapper.getFQN(psiFile)
      else JavaEditorFileSwapper.getFQN(psiFile)
    if (fqn == null) return null
    if (isScala && ScalaEditorFileSwapper.findSourceFile(myProject, file) != null) return null
    if (!isScala && JavaEditorFileSwapper.findSourceFile(myProject, file) != null) return null
    val panel: EditorNotificationPanel = new EditorNotificationPanel
    val sourceFile: VirtualFile = findSourceFile(file)
    var defaultAction: AttachSourcesProvider.AttachSourcesAction = null
    if (sourceFile != null) {
      panel.setText(ProjectBundle.message("library.sources.not.attached"))
      defaultAction = new AttachSourcesUtil.AttachJarAsSourcesAction(file, sourceFile, myProject)
    }
    else {
      panel.setText(ProjectBundle.message("library.sources.not.found"))
      defaultAction = new AttachSourcesUtil.ChooseAndAttachSourcesAction(myProject)
    }



    val actions: TreeSet[AttachSourcesProvider.AttachSourcesAction] =
      new TreeSet[AttachSourcesProvider.AttachSourcesAction](new Comparator[AttachSourcesProvider.AttachSourcesAction] {
        def compare(o1: AttachSourcesProvider.AttachSourcesAction,
                    o2: AttachSourcesProvider.AttachSourcesAction): Int = {
          if (o1 eq defaultAction) return 1
          if (o2 eq defaultAction) return -1
          o1.getName.compareToIgnoreCase(o2.getName)
        }
      })



    actions.add(defaultAction)


    for (each <- Extensions.getExtensions(EXTENSION_POINT_NAME)) {
      actions.addAll(each.getActions(libraries, psiFile))
    }

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
              SwingUtilities.invokeLater(new Runnable {
                def run() {
                  panel.setText(ProjectBundle.message("library.sources.not.found"))
                }
              })
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

  private def findOrderEntriesContainingFile(file: VirtualFile): List[LibraryOrderEntry] = {
    val libs: List[LibraryOrderEntry] = new ArrayList[LibraryOrderEntry]
    val entries: List[OrderEntry] = ProjectRootManager.getInstance(myProject).getFileIndex.getOrderEntriesForFile(file)
    import scala.collection.JavaConversions._
    for (entry <- entries) {
      if (entry.isInstanceOf[LibraryOrderEntry]) {
        libs.add(entry.asInstanceOf[LibraryOrderEntry])
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
}