package org.jetbrains.plugins.scala.project.settings

import java.awt._
import java.awt.event.MouseEvent
import java.util

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionManager, AnActionEvent, ShortcutSet}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.{InputValidatorEx, Messages, Splitter}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Comparing, Key}
import com.intellij.ui._
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.{EditableTreeModel, JBUI}
import javax.swing._
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel, TreePath}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerProfilesPanel._

import scala.collection.JavaConverters._

/**
 * NOTE: This was initially almost the exact clone of [[com.intellij.compiler.options.AnnotationProcessorsPanel]]
 * later converted to Scala with some refactorings
 */
class ScalaCompilerProfilesPanel(val myProject: Project) extends JPanel(new BorderLayout) {

  private val myDefaultProfile = new ScalaCompilerSettingsProfile("") // TODO: make immutable?
  private var myModuleProfiles = Seq.empty[ScalaCompilerSettingsProfile]

  private val myAllModulesMap  = ModuleManager.getInstance(myProject).getModules.groupBy(_.getName).mapValues(_.head)

  private val myTree          = new Tree(new MyTreeModel)
  private val mySettingsPanel = new ScalaCompilerSettingsPanel // right panel

  private var mySelectedProfile: ScalaCompilerSettingsProfile = _

  initPanel()

  private def initPanel(): Unit = {
    val splitter = new Splitter(false, 0.3f)
    add(splitter, BorderLayout.CENTER)

    val treePanel = ToolbarDecorator.createDecorator(myTree)
      .addExtraAction(new MoveToAction)
      .createPanel
    splitter.setFirstComponent(treePanel)

    myTree.setRootVisible(false)
    myTree.setCellRenderer(new MyCellRenderer)
    myTree.addTreeSelectionListener(onNodeSelected)

    val settingsComponent = mySettingsPanel.getComponent
    settingsComponent.setBorder(JBUI.Borders.emptyLeft(6))
    splitter.setSecondComponent(settingsComponent)

    val search = new TreeSpeedSearch(myTree)
    search.setComparator(new SpeedSearchComparator(false))
  }

  private def onNodeSelected(__ : TreeSelectionEvent): Unit =
    for {
      selectedNodeProfile <- getSelectedProfileNode(myTree).map(_.profile)
      if selectedNodeProfile != mySelectedProfile
    } {
      if (mySelectedProfile != null) {
        mySettingsPanel.saveTo(mySelectedProfile)
      }
      mySelectedProfile = selectedNodeProfile
      mySettingsPanel.setProfile(selectedNodeProfile)
    }

  private def getSelectedProfileNode(tree: Tree): Option[ProfileNode] =
    Option(tree.getSelectionPath).flatMap { path =>
      val node = path.getLastPathComponent match {
        case moduleNode: MyModuleNode => moduleNode.getParent
        case n                        => n
      }
      Option(node).filterByType[ProfileNode]
    }

  def getDefaultProfile: ScalaCompilerSettingsProfile = {
    val selectedProfile = mySelectedProfile
    if (myDefaultProfile == selectedProfile)
      mySettingsPanel.saveTo(selectedProfile)
    myDefaultProfile
  }

  def getModuleProfiles: Seq[ScalaCompilerSettingsProfile] = {
    val selectedProfile = mySelectedProfile
    if (myDefaultProfile != selectedProfile)
      mySettingsPanel.saveTo(selectedProfile)
    myModuleProfiles
  }

  def initProfiles(defaultProfile: ScalaCompilerSettingsProfile, moduleProfiles: Seq[ScalaCompilerSettingsProfile]): Unit = {
    myDefaultProfile.initFrom(defaultProfile)
    myModuleProfiles = moduleProfiles.map { profile =>
      val copy = new ScalaCompilerSettingsProfile("") // TODO: make immutable
      copy.initFrom(profile)
      copy
    }
    val root = myTree.getModel.getRoot.asInstanceOf[RootNode]
    root.sync()
    preselectProfile(root)
  }

  private def preselectProfile(root: RootNode): Unit = {
    val tempSelectProfile = getTemporarySelectProfile(myProject).flatMap(findProfileNodeWithName(root, _))
    val nodeToSelect = tempSelectProfile.orElse(Option(TreeUtil.findNodeWithObject(root, myDefaultProfile)))
    nodeToSelect.foreach { node =>
      TreeUtil.selectNode(myTree, node)
      clearTemporarySelectProfile(myProject)
    }
  }

  private def findProfileNodeWithName(root: RootNode, profileName: String): Option[DefaultMutableTreeNode] =
    Option(TreeUtil.findNode(root, {
      case node: ProfileNode => node.profile.getName == profileName
      case _                 => false
    }))

  private class MyTreeModel() extends DefaultTreeModel(new RootNode) with EditableTreeModel {
    override def addNode(parentOrNeighbour: TreePath): TreePath = {
      val newProfileName = readProfileNameInDialog()
      newProfileName.foreach(createNewProfile)
      null
    }

    private def createNewProfile(newProfileName: String): Unit = {
      val profile = new ScalaCompilerSettingsProfile(newProfileName)
      myModuleProfiles = myModuleProfiles :+ profile
      getRoot.asInstanceOf[DataSynchronizable].sync()
      val node = TreeUtil.findNodeWithObject(getRoot.asInstanceOf[DefaultMutableTreeNode], profile)
      if (node != null) {
        TreeUtil.selectNode(myTree, node)
      }
    }

    private def readProfileNameInDialog(): Option[String] = {
      val result = Messages.showInputDialog(myProject,
        ScalaBundle.message("scala.compiler.profiles.panel.profile.name"),
        ScalaBundle.message("scala.compiler.profiles.panel.create.new.profile"),
        null, "",
        new ProfileNameValidator
      )
      Option(result)
    }

    private class ProfileNameValidator extends InputValidatorEx {
      override def checkInput(inputString: String): Boolean = {
        if (StringUtil.isEmpty(inputString)) return false
        if (Comparing.equal(inputString, myDefaultProfile.getName)) return false
        !myModuleProfiles.exists(p => Comparing.equal(inputString, p.getName))
      }

      override def canClose(inputString: String): Boolean = checkInput(inputString)

      override def getErrorText(inputString: String): String = {
        if (checkInput(inputString)) return null
        if (StringUtil.isEmpty(inputString)) {
          ScalaBundle.message("scala.compiler.profiles.panel.profile.should.not.be.empty")
        } else {
          ScalaBundle.message("scala.compiler.profiles.panel.profile.already.exists", inputString)
        }
      }
    }

    override def removeNode(nodePath: TreePath): Unit = {
      nodePath.getLastPathComponent match {
        case node: ProfileNode =>
          val nodeProfile = node.profile
          if (nodeProfile != myDefaultProfile) {
            if (mySelectedProfile == nodeProfile)
              mySelectedProfile = null
            myModuleProfiles = myModuleProfiles.filter(_ != nodeProfile)
            getRoot.asInstanceOf[DataSynchronizable].sync()
            val foundNode = TreeUtil.findNodeWithObject(getRoot.asInstanceOf[DefaultMutableTreeNode], myDefaultProfile)
            if (foundNode != null) {
              TreeUtil.selectNode(myTree, foundNode)
            }
          }
        case _  =>
      }
    }

    override def removeNodes(path: util.Collection[_ <: TreePath]): Unit = ()

    override def moveNodeTo(parentOrNeighbour: TreePath): Unit = ()
  }

  private class MoveToAction
    extends AnActionButton(ScalaBundle.message("scala.compiler.profiles.panel.move.to"), AllIcons.Actions.Forward) {

    override def getShortcut: ShortcutSet =
      ActionManager.getInstance.getAction("Move").getShortcutSet

    override def isEnabled: Boolean =
      myTree.getSelectionPath match {
        case null  => false
        case entry => entry.getLastPathComponent.isInstanceOf[MyModuleNode] && myModuleProfiles.nonEmpty
      }

    override def actionPerformed(e: AnActionEvent): Unit = {
      val selectionPath = myTree.getSelectionPath
      if (selectionPath == null) return

      val moduleNode  = selectionPath.getLastPathComponent.asInstanceOf[MyModuleNode]
      val moduleProfile = {
        val profileNode = moduleNode.getParent.asInstanceOf[ProfileNode]
        profileNode.profile
      }
      val otherProfiles = allProfiles.filter(_ != moduleProfile).toList
      val popup = JBPopupFactory.getInstance
        .createPopupChooserBuilder(otherProfiles.asJava)
        .setTitle(ScalaBundle.message("scala.compiler.profiles.panel.move.to"))
        .setItemChosenCallback { profile =>
          if (profile != null) {
            onProfileSelected(moduleNode, moduleProfile, profile)
          }
        }
        .createPopup
      val point = relativePoint(e)
      popup.show(point)
    }

    private def allProfiles: Seq[ScalaCompilerSettingsProfile] = Seq(myDefaultProfile) ++ myModuleProfiles

    private def onProfileSelected(moduleNode: MyModuleNode, nodeProfile: ScalaCompilerSettingsProfile, selectedProfile: ScalaCompilerSettingsProfile): Unit = {
      val selectedNodes = Option(myTree.getSelectionPaths).getOrElse(Array())
      val selectedModules = selectedNodes.map(_.getLastPathComponent).collect { case n: MyModuleNode => n.module }
      selectedModules
        .foreach { module =>
          if (nodeProfile != myDefaultProfile) {
            nodeProfile.removeModuleName(module.getName)
          }
          if (selectedProfile != myDefaultProfile) {
            selectedProfile.addModuleName(module.getName)
          }
        }

      val root = myTree.getModel.getRoot.asInstanceOf[RootNode]
      root.sync()
      val node1 = TreeUtil.findNodeWithObject(root, moduleNode.module)
      if (node1 != null) {
        TreeUtil.selectNode(myTree, node1)
      }
    }

    private def relativePoint(e: AnActionEvent): RelativePoint = {
      val point =
        if (e.getInputEvent.isInstanceOf[MouseEvent]) getPreferredPopupPoint
        else TreeUtil.getPointForSelection(myTree)
      if (point != null) point
      else TreeUtil.getPointForSelection(myTree)
    }
  }

  private class RootNode extends DefaultMutableTreeNode with DataSynchronizable {
    override def sync(): this.type = {
      val newKids = Seq(new ProfileNode(myDefaultProfile, this, true).sync()) ++
        myModuleProfiles.map(p => new ProfileNode(p, this, false).sync())
      children = new util.Vector(newKids.asJava)
      myTree.getModel.asInstanceOf[DefaultTreeModel].reload()
      expand(myTree)
      this
    }
  }

  private class ProfileNode(val profile: ScalaCompilerSettingsProfile, parent: RootNode, isDefault: Boolean)
    extends DefaultMutableTreeNode(profile)
      with DataSynchronizable {

    setParent(parent)

    override def sync(): this.type = {
      val nodeModules: Seq[Module] = if (isDefault) {
        val nonDefaultProfileModules = myModuleProfiles.flatMap(_.moduleNames).toSet
        myAllModulesMap.toSeq.collect { case (key, value) if !nonDefaultProfileModules.contains(key) => value }
      } else {
        profile.moduleNames.flatMap(myAllModulesMap.get)
      }
      val newChildren = nodeModules
        .sortBy(_.getName)
        .map(m => new MyModuleNode(m, this))
        .asJava
      children = new util.Vector(newChildren)
      this
    }
  }

  private class MyModuleNode(val module: Module, parent: ProfileNode) extends DefaultMutableTreeNode(module) {
    setParent(parent)
    setAllowsChildren(false)
  }

  private class MyCellRenderer extends ColoredTreeCellRenderer {

    override def customizeCellRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Unit =
      value match {
        case node: ProfileNode  =>
          append(node.profile.getName)
        case node: MyModuleNode =>
          val module = node.getUserObject.asInstanceOf[Module]
          setIcon(AllIcons.Nodes.Module)
          //noinspection ReferencePassedToNls
          append(module.getName)
        case _ =>
      }
  }
}

object ScalaCompilerProfilesPanel {

  // used like a global variable within a project to pass context about which profile should we select on settings panel open
  // not intended to be persisted (for now) so should be reset once read
  private val SELECTED_PROFILE_NAME = new Key[String]("SelectedScalaCompilerProfileName")

  private def clearTemporarySelectProfile(project: Project): Unit =
    project.putUserData(ScalaCompilerProfilesPanel.SELECTED_PROFILE_NAME, null)

  private def getTemporarySelectProfile(project: Project): Option[String] =
    Option(project.getUserData(ScalaCompilerProfilesPanel.SELECTED_PROFILE_NAME))

  def withTemporarySelectedProfile[T](project: Project, profileName: String)(body: => T): T =
    try {
      project.putUserData(SELECTED_PROFILE_NAME, profileName)
      body
    } finally {
      project.putUserData(SELECTED_PROFILE_NAME, null)
    }

  private def expand(tree: JTree): Unit = {
    var oldRowCount = 0
    do {
      val rowCount = tree.getRowCount
      if (rowCount == oldRowCount) return
      oldRowCount = rowCount
      for (i <- 0 until rowCount) {
        tree.expandRow(i)
      }
    } while (true)
  }

  private trait DataSynchronizable {
    def sync(): this.type
  }
}