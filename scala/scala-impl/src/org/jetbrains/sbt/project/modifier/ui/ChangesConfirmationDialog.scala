package org.jetbrains.sbt.project.modifier.ui

import java.awt.BorderLayout

import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.project.{Project => IJProject}
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.{JComponent, JPanel}
import org.jetbrains.sbt.SbtBundle

import scala.jdk.CollectionConverters._
import scala.collection.mutable


/**
 * A confirmation dialog for build file changes. Uses basic vcs components to display difference between changed copy
 * and original build file. Notice that a trick is used to display proper paths in changes tree view: changes have
 * 'before/'after' revisions switch places so that in tree view path to 'before' is used while in diff view revisions
 * are in default places.
 * @author Roman.Shein
 * @since 19.03.2015.
 */
class ChangesConfirmationDialog private (val project: IJProject, private val changes: List[BuildFileChange], var myChangesBrowser: BuildFileChangeBrowser,
                                         val fileStatusMap: mutable.Map[VirtualFile, (BuildFileModifiedStatus, Long)], val canExcludeChanges: Boolean = false) extends DialogWrapper(project) {

  setTitle(SbtBundle.message("sbt.build.file.changes"))
  init()

  def selectedChanges: List[BuildFileChange] = {
    myChangesBrowser
      .getViewer
      .getIncludedChanges
      .asScala
      .map(change => BuildFileChange.swap(change.asInstanceOf[BuildFileChange]))
      .toList
  }

  override def createCenterPanel(): JComponent = {
    val rootPane: JPanel = new JPanel(new BorderLayout)

    val swappedChanges: java.util.ArrayList[Change] = new java.util.ArrayList[Change]()
    swappedChanges.addAll(changes.map(BuildFileChange.swap).asJavaCollection)
    val changesBrowser = new BuildFileChangeBrowser(project, swappedChanges, canExcludeChanges, fileStatusMap)
    myChangesBrowser = changesBrowser
    changesBrowser.setChangesToDisplay(swappedChanges)
    changesBrowser.addToolbarAction(ActionManager.getInstance.getAction(IdeActions.ACTION_EDIT_SOURCE))
    rootPane.add(changesBrowser)

    /*todo val diffDetails = new ShortDiffDetails(project, new Getter[Array[Change]] {
      def get: Array[Change] = {
        val selectedChanges = changesBrowser.getViewer.getSelectedChanges
        selectedChanges.toArray(new Array[Change](selectedChanges.size))
      }
    }, VcsChangeDetailsManager.getInstance(project))
    diffDetails.setParent(changesBrowser)*/

    rootPane
  }

}

object ChangesConfirmationDialog {
  def apply(project: IJProject, changes: List[BuildFileChange], fileChangesMap: mutable.Map[VirtualFile, (BuildFileModifiedStatus, Long)]): ChangesConfirmationDialog =
    new ChangesConfirmationDialog(project, changes, null, fileChangesMap)
}
