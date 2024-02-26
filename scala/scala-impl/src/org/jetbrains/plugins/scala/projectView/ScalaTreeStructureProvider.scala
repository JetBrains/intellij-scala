package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.canRealModuleNameBeHidden
import com.intellij.ide.projectView.impl.nodes.{ProjectViewModuleGroupNode, ProjectViewProjectNode, PsiDirectoryNode, PsiFileSystemItemFilter}
import com.intellij.ide.projectView.{PresentationData, TreeStructureProvider, ViewSettings}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule
import com.intellij.openapi.module.{Module, ModuleGrouper, ModuleManager}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiDirectory
import com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.sbt.project.SbtProjectSystem

import java.util
import scala.jdk.CollectionConverters._
import scala.util.control.Breaks._


final class ScalaTreeStructureProvider extends TreeStructureProvider with DumbAware {

  import ScalaTreeStructureProvider._


  override def modify(parent: Node, children: util.Collection[Node], settings: ViewSettings): util.Collection[Node] = {
    val project = parent.getProject
    if (project == null) return children

    val childrenSeq = children.asScala.toSeq
    val modifiedChildren = parent match {
      // note: in GradleTreeStructureProvider they also implemented special handling for ProjectViewModuleGroupNode.
      // It is more important in Gradle because they allow modules to be created outside the project base directory and in such cases it may happen
      // that ProjectViewModuleGroupNode will be a parent.
      // In sbt I have not been able to find any standard case in which we would need it. I was able to recreate such situation
      // by e.g. removing content root in the root module, but it is not our standard case, so I think we shouldn't care about it.
      // recheck it when SCL-21157 is fixed
      case _: ProjectViewProjectNode =>
        getProjectViewProjectNodeChildren(childrenSeq)(project, settings)
      case _ =>
        childrenSeq.map { it =>
          transform(it)(it.getProject, settings)
        }
    }
    modifiedChildren.asJavaCollection
  }
}

private object ScalaTreeStructureProvider {

  // note: this logic is written based on like the case where the parent is ProjectViewProjectNode is implemented in
  // org.jetbrains.plugins.gradle.projectView.GradleTreeStructureProvider#getProjectNodeChildren
  private def getProjectViewProjectNodeChildren(children: Seq[Node])
                                (implicit project: Project, settings: ViewSettings): Seq[Node] = {
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    children.map {
          case projectViewModuleGroupNode: ProjectViewModuleGroupNode =>
            val directoryNode = getProjectViewModuleGroupNodeDirectoryNode(projectViewModuleGroupNode, fileIndex)
            directoryNode.getOrElse(projectViewModuleGroupNode)
          case psiDirectoryNode: PsiDirectoryNode if psiDirectoryNode.getParent == null && psiDirectoryNode.getValue != null =>
            val scalaModuleDirectoryNode = getScalaModuleDirectoryNode(psiDirectoryNode)
            scalaModuleDirectoryNode.getOrElse(psiDirectoryNode)
          case node =>
            transform(node)
    }
  }

  // note: this logic is written based on like the case where the parent is ProjectViewProjectNode is implemented in
  // org.jetbrains.plugins.gradle.projectView.GradleTreeStructureProvider#getProjectNodeChildren
  private def getProjectViewModuleGroupNodeDirectoryNode(projectViewModuleGroupNode: ProjectViewModuleGroupNode, fileIndex: ProjectFileIndex): Option[PsiDirectoryNode] = {
    val children = projectViewModuleGroupNode.getChildren.asScala.toSeq
    val collectedChildren = children.collect {
      case child: PsiDirectoryNode if {
        val psiDirectory = child.getValue
        psiDirectory != null && {
          val module = fileIndex.getModuleForFile(psiDirectory.getVirtualFile)
          isExternalSystemAwareModule(SbtProjectSystem.Id, module)
        }
      } => (child.getValue.getVirtualFile, child)
    }

    if (collectedChildren.length < children.length) return None

    var parentNodePair: Option[(VirtualFile, PsiDirectoryNode)] = None
    breakable {
      collectedChildren.foreach { case (virtualFile, psiDirectoryNode) =>
        parentNodePair match {
          case None =>
            parentNodePair = Option(virtualFile, psiDirectoryNode)
          case Some((file, _)) if VfsUtilCore.isAncestor(virtualFile, file, false) =>
            parentNodePair = Option(virtualFile, psiDirectoryNode)
          case Some((file, _)) if !VfsUtilCore.isAncestor(file, virtualFile, false) =>
            parentNodePair = None
            break()
          case _ =>
        }
      }
    }
    parentNodePair.map(_._2)
  }

  private def getScalaModuleDirectoryNode(node: PsiDirectoryNode)
                                         (implicit project: Project, settings: ViewSettings): Option[ScalaModuleDirectoryNode] =
    getScalaModuleDirectoryNode(node.getValue, node.getFilter)

  private def getScalaModuleDirectoryNode(
    psiDirectory: PsiDirectory,
    filter: PsiFileSystemItemFilter
  )(implicit project: Project, settings: ViewSettings) : Option[ScalaModuleDirectoryNode] = {
    val virtualFile = psiDirectory.getVirtualFile
    // For now in the process of creating modules, a single content root for each module is created and its path is equal to project.base.path (it is the root of the module).
    // In ProjectRootsUtil#isModuleContentRoot it is checked whether the virtualFile is equal to the content root path associated with this virtualFile.
    // In a nutshell, with this we check whether virtualFile is the module root. If it is, there is some probability that we should create
    // ScalaModuleDirectoryNode for this node.
    if (!ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) return None
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    val module = fileIndex.getModuleForFile(virtualFile)
    if (module == null) return None
    val moduleShortName = getModuleShortName(module, project, virtualFile)
    moduleShortName
      .map(ScalaModuleDirectoryNode(project, psiDirectory, settings, _, filter, module))
  }

  private def transform(node: Node)
                       (implicit project: Project, settings: ViewSettings): Node = {
    val nodeValue = node.getValue
    nodeValue match {
      case psiDirectory: PsiDirectory =>
        getScalaModuleDirectoryNode(psiDirectory, node.asInstanceOf[PsiDirectoryNode].getFilter)
          .getOrElse(node)
      case file: ScalaFile =>
        Node(file)
      case definition: ScTypeDefinition  =>
        node match {
          case _: TypeDefinitionNode =>
            node
          case _ =>
            //Scala type definition can be wrapped into non-TypeDefinitionNode in some other places in the platform
            //For example in com.intellij.ide.projectView.impl.ClassesTreeStructureProvider.doModify
            //ClassTreeNode is returned if file contains single class/trait/object definition
            //This is done in case file name equals to the type name
            //And this is even if the file contains other top level definitions in Scala 3 (def, val, etc...)
            //In this workaround we recalculate node for any definition which happens to be in Scala File
            if (definition.isTopLevel)
              definition.getContainingFile match {
                case file: ScalaFile =>
                  Node(file)
                case _ =>
                  new TypeDefinitionNode(definition)
              }
            else
              new TypeDefinitionNode(definition)
        }
      case _ =>
        node
    }
  }

  private def getModuleShortName(module: Module, project: Project, virtualFile: VirtualFile): Option[String] = {
    if (!isExternalSystemAwareModule(SbtProjectSystem.Id, module)) return None

    // note: generating module short name shouldn't be done for root modules in a multi BUILD project (root module represents root project in each BUILD)
    // This is how it is implemented, because when there is a project with multi BUILD, and projects from different BUILDs are grouped together, it
    // is more transparent to display full module name for root modules -it may simplify searching concrete modules in Project Structure | Modules
    if (isRootModuleInMultiBUILDProject(module, project, virtualFile)) return None

    val fullModuleName = module.getName
    val moduleGrouper = ModuleGrouper.instanceFor(project)
    val shortModuleName = moduleGrouper.getShortenedNameByFullModuleName(fullModuleName)

    // Because of the fact that ExplicitModuleGrouper#getShortenedNameByFullModuleName always returns the original module name and
    // QualifiedNameGrouper#getShortenedNameByFullModuleName also returns the original module name when when grouping is not used at all in the project, we can assume that
    // when (shortModuleName == moduleName) it is not needed to create custom ScalaModuleDirectoryNode (so None is returned from this method).
    // For such a case com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.updateImpl will work correctly, because the group name is not present in module name
    if (fullModuleName == shortModuleName || shortModuleName.isBlank) None else Some(shortModuleName)
  }


  private def isRootModuleInMultiBUILDProject(module: Module, project: Project, virtualFile: VirtualFile): Boolean = {
    val regexPattern = (path: String) => s""".*$path(?:/)?\\]""".r
    val moduleRegexPattern = regexPattern(virtualFile.getPath)

    def moduleIdOpt(module: Module): Option[String] = Option(ExternalSystemApiUtil.getExternalProjectId(module))

    def isRootAndBelongsToDifferentBUILD(module: Module): Boolean = {
      moduleIdOpt(module).fold(false) { id =>
        val moduleRootPath = ExternalSystemApiUtil.getExternalProjectPath(module)
        val isRoot = regexPattern(moduleRootPath).matches(id)
        isRoot && !moduleRegexPattern.matches(id)
      }
    }

    moduleIdOpt(module).exists { id =>
      val isRootProject = moduleRegexPattern.matches(id)
      if (isRootProject) {
        // note: checking if there are more root projects and if they belong to other BUILD
        val modules = ModuleManager.getInstance(project).getModules
        modules.exists(isRootAndBelongsToDifferentBUILD)
      } else {
        false
      }
    }
  }

}

private case class ScalaModuleDirectoryNode(
  project: Project,
  psiDirectory: PsiDirectory,
  settings: ViewSettings,
  @NlsSafe moduleShortName: String,
  filter: PsiFileSystemItemFilter,
  module: Module,
) extends PsiDirectoryNode(project, psiDirectory, settings, filter) {

  private lazy val moduleShortNameMatchesDirectoryName = StringUtil.equalsIgnoreCase(
      moduleShortName.replace("-", ""),
      psiDirectory.getVirtualFile.getName.replace("-", "")
  )

  override def shouldShowModuleName(): Boolean = canRealModuleNameBeHidden

  override def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)
    if (!canRealModuleNameBeHidden) {
      if (!moduleShortNameMatchesDirectoryName) {
        data.addText("[" + moduleShortName + "]", REGULAR_BOLD_ATTRIBUTES)
      } else {
        data.clearText()
        data.addText(moduleShortName, REGULAR_BOLD_ATTRIBUTES)
      }
    }
  }
}
