package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.{ModuleGroup, ProjectRootsUtil}
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.canRealModuleNameBeHidden
import com.intellij.ide.projectView.impl.nodes.{ProjectViewModuleGroupNode, ProjectViewModuleNode, ProjectViewProjectNode, PsiDirectoryNode, PsiFileSystemItemFilter}
import com.intellij.ide.projectView.{PresentationData, TreeStructureProvider, ViewSettings}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.{getExternalProjectPath, isExternalSystemAwareModule}
import com.intellij.openapi.module.{Module, ModuleGrouper, ModuleManager}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.roots.{ModuleRootManager, ProjectRootManager}
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiDirectory
import com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.util.SbtModuleType
import org.jetbrains.sbt.project.SbtProjectSystem

import java.util
import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.control.Breaks._


final class ScalaTreeStructureProvider extends TreeStructureProvider with DumbAware {

  import ScalaTreeStructureProvider._


  override def modify(parent: Node, children: util.Collection[Node], settings: ViewSettings): util.Collection[Node] = {
    val project = parent.getProject
    if (project == null) return children

    val childrenSeq = children.asScala.toSeq
    val modifiedChildren = parent match {
      case moduleGroupNode: ProjectViewModuleGroupNode =>
        val topLevelDirectories = moduleGroupNode match {
          case node: ScalaProjectViewModuleGroupNode => node.projectViewTopLevelDirectories
          case _ => Nil
        }
        transformProjectViewModuleGroupNodeChildren(childrenSeq, topLevelDirectories)(project)
      case _: ProjectViewProjectNode =>
        transformProjectViewProjectNodeChildren(childrenSeq)(project, settings)
      case _ =>
        childrenSeq.map { it => transform(it)(it.getProject, settings) }
    }
    modifiedChildren.asJavaCollection
  }
}

private object ScalaTreeStructureProvider {

  /**
   * @param projectViewTopLevelDirectories see [[ScalaProjectViewModuleGroupNode]]
   */
  private def transformProjectViewModuleGroupNodeChildren(children: Seq[Node], projectViewTopLevelDirectories: Seq[VirtualFile])(implicit project: Project): Seq[Node] = {
    val moduleChildren = children.collect {
      case projectViewModuleNode: ProjectViewModuleNode =>
        // note: in Gradle when #showUnderModuleGroup returns true for the given module, it is possible to create a special object -
        // GradleProjectViewModuleNode. Later explicit handling for it is done. In sbt I couldn't find a case in which it would be needed.
        // That's because ProjectViewModuleNode might be created when a module has empty/non-existent/many content roots. In our implementation it is only possible
        // for SbtSourceSetData modules for which #showUnderModuleGroup always returns false
        (projectViewModuleNode, projectViewModuleNode.getValue, None)
      case psiDirectoryNode: PsiDirectoryNode =>
        val virtualFile = psiDirectoryNode.getValue.getVirtualFile
        (psiDirectoryNode, getModuleFromVirtualFile(virtualFile), Some(virtualFile))
    }

    val collectedNodes = moduleChildren.map(_._1)
    val otherNodes = children.diff(collectedNodes)

    val sortedModuleChildren = moduleChildren.sortBy(_._3)(Ordering.fromLessThan {
      case (None, Some(_)) => false
      case (Some(x1), Some(y1)) => VfsUtilCore.compareByPath(x1, y1) < 0
      case _ => true
    })

    val moduleChildrenToDisplay = sortedModuleChildren.foldLeft((projectViewTopLevelDirectories.toSet, Seq.empty[Node])) {
      // Collecting displayed paths under the module group is necessary to prevent modules from being displayed twice under the group node in certain cases.
      // It doesn't prevent directories from being displayed twice under the module group in all scenarios, but it does address the issue described in SCL-22194.
      case ((displayedPaths, nodes), (node, module, psiDirectoryNodeFile)) =>
        val shouldShow = showUnderModuleGroup(module, displayedPaths)
        if (shouldShow)
          (psiDirectoryNodeFile.fold(displayedPaths)(file => displayedPaths + file), nodes :+ node)
        else
          (displayedPaths, nodes)
    }._2

    moduleChildrenToDisplay ++ otherNodes
  }

  /**
  * The logic of this method has been written on the basis of how ProjectViewProjectNode is handled in
  * [[org.jetbrains.plugins.gradle.projectView.GradleTreeStructureProvider#getProjectNodeChildren]]
  */
  private def transformProjectViewProjectNodeChildren(children: Seq[Node])(implicit project: Project, settings: ViewSettings): Seq[Node] = {
    val projectViewPsiDirectoriesFiles = children.collect { case node: PsiDirectoryNode => node.getValue.getVirtualFile }
    children.map {
      case projectViewModuleGroupNode: ProjectViewModuleGroupNode =>
        val psiDirectoryNode = convertGroupNodeToPsiDirectoryNode(projectViewModuleGroupNode)
        psiDirectoryNode.getOrElse {
          ScalaProjectViewModuleGroupNode(project, projectViewModuleGroupNode.getValue, settings, projectViewPsiDirectoriesFiles)
        }
      case psiDirectoryNode: PsiDirectoryNode if psiDirectoryNode.getParent == null && psiDirectoryNode.getValue != null =>
        val scalaModuleDirectoryNode = getScalaModuleDirectoryNode(psiDirectoryNode)
        scalaModuleDirectoryNode.getOrElse(psiDirectoryNode)
      case node =>
        transform(node)
    }
  }

  /**
   * Convert the [[ProjectViewModuleGroupNode]] to [[PsiDirectoryNode]], if all children within the group are
   * supported [[PsiDirectoryNode]] instances. A detailed explanation of this method, along with an example, is provided in SCL-22171.
   *
   * @note It is written based on [[org.jetbrains.plugins.gradle.projectView.GradleTreeStructureProvider.getProjectNodeChildren]]
   */
  private def convertGroupNodeToPsiDirectoryNode(
    projectViewModuleGroupNode: ProjectViewModuleGroupNode
  )(implicit project: Project): Option[PsiDirectoryNode] = {
    val children = projectViewModuleGroupNode.getChildren.asScala.toSeq
    val collectedChildren = children.collect {
      case child: PsiDirectoryNode if {
        val psiDirectory = child.getValue
        psiDirectory != null && {
          val module = getModuleFromVirtualFile(psiDirectory.getVirtualFile)
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

  @Nullable
  private def getModuleFromVirtualFile(virtualFile: VirtualFile)
                                      (implicit project: Project): Module = {
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    fileIndex.getModuleForFile(virtualFile)
  }

  /**
   * It is partially written based on [[org.jetbrains.plugins.gradle.projectView.GradleTreeStructureProvider#showUnderModuleGroup]]
   */
  private def showUnderModuleGroup(@Nullable module: Module, displayedPaths: Set[VirtualFile]): Boolean = {
    if (module == null) return false

    val externalModulePath = getExternalProjectPath(module)
    if (externalModulePath == null) return false

    !isExternalSystemAwareModule(SbtProjectSystem.Id, module) ||
      ModuleRootManager.getInstance(module).getContentRoots.exists { root =>
        val contentRootPath = root.getPath
        val isNotAncestorOfModulePath = !isAncestor(externalModulePath, contentRootPath)
        val isContentRootUnderDisplayedPaths = displayedPaths.exists(VfsUtilCore.isAncestor(_, root, true))
        isNotAncestorOfModulePath && !isContentRootUnderDisplayedPaths
      }
  }

  /**
   * @param strict if `true`, it means that the file cannot be an ancestor of itself
   */
  private def isAncestor(ancestor: String, file: String, strict: Boolean = true): Boolean =
    FileUtil.isAncestor(ancestor, file, strict)

  private def getScalaModuleDirectoryNode(node: PsiDirectoryNode)(implicit project: Project, settings: ViewSettings): Option[ScalaModuleDirectoryNode] =
    getScalaModuleDirectoryNode(node.getValue, node.getFilter)

  private def getScalaModuleDirectoryNode(
    psiDirectory: PsiDirectory,
    @Nullable filter: PsiFileSystemItemFilter
  )(implicit project: Project, settings: ViewSettings) : Option[ScalaModuleDirectoryNode] = {
    val virtualFile = psiDirectory.getVirtualFile
    // In ProjectRootsUtil#isModuleContentRoot it is checked whether the virtualFile is equal to the content root path associated with this virtualFile.
    // If this happens, it means that we are dealing with a module root and maybe ScalaModuleDirectoryNode will have to be created.
    if (!ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) return None
    val module = getModuleFromVirtualFile(virtualFile)
    if (module == null) return None
    val moduleShortName = getModuleShortName(module, project, virtualFile)
    moduleShortName
      .map(ScalaModuleDirectoryNode(project, psiDirectory, settings, _, filter, module))
  }

  private def transform(node: Node)
                       (implicit project: Project, settings: ViewSettings): Node = {
    val nodeValue = node.getValue
    nodeValue match {
      case _: PsiDirectory =>
        node match {
          case x: PsiDirectoryNode => getScalaModuleDirectoryNode(x).getOrElse(node)
          case _ => node
        }
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

    // note: generating module short name shouldn't be done for root modules in a multi build project (root module represents a root project in each build)
    // This is how it is implemented, because when there is a project with multi build, and projects from different builds are grouped together, it
    // is more transparent to display full module name for root modules -it may simplify searching concrete modules in Project Structure | Modules
    if (isRootModuleInMultiBuildProject(module, project, virtualFile)) return None

    val fullModuleName = module.getName
    val isModuleUnderItsParent = isModuleUnderItsRealParent(module, project, virtualFile)

    val shortModuleName =
      if (!isModuleUnderItsParent) {
        // If the source set module is not placed under its parent, we take the last two elements from the full internal module name.
        // In practice, this represents the parent module name (excluding any group, if present) and the source set name, such as main or test
        fullModuleName.split('.').takeRight(2).mkString(".")
      } else {
        val moduleGrouper = ModuleGrouper.instanceFor(project)
        // #getShortenedNameByFullModuleName splits the module name by dots and returns the last element
        moduleGrouper.getShortenedNameByFullModuleName(fullModuleName)
      }

    // Because ExplicitModuleGrouper#getShortenedNameByFullModuleName always returns the original module name and
    // QualifiedNameGrouper#getShortenedNameByFullModuleName also returns the original module name when grouping is not used at all in the project, we can assume that
    // when (shortModuleName == moduleName) it is not needed to create custom ScalaModuleDirectoryNode (so None is returned from this method).
    // For such a case com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.updateImpl will work correctly, because the group name is not present in module name
    if (fullModuleName == shortModuleName || shortModuleName.isBlank) None else Some(shortModuleName)
  }

  /**
   * Checks whether a module is a source set module (main/test) and, if so, verifies
   * whether it is correctly placed under its parent module.
   *
   * @return `true` if the module is not a source set module, or if it is a source set
   *         module and is correctly placed under its parent module
   */
  private def isModuleUnderItsRealParent(module: Module, project: Project, virtualFile: VirtualFile): Boolean = {
    if (!isSourceSetModule(module)) return true

    val projectRoot = ExternalSystemApiUtil.getExternalRootProjectPath(module)
    def isInsideProjectRoot(parent: VirtualFile): Boolean =
      isAncestor(projectRoot, parent.getPath, strict = false)

    val moduleName = module.getName
    // The source set suffix may include an appended number (e.g., main~1),
    // so it is necessary to extract the exact source set suffix instead of using hardcoded main/test values
    val sourceSetSuffix = moduleName.split('.').lastOption match {
      case Some(suffix) => suffix
      case None => return true
    }
    @tailrec
    def isFileTheCorrectParent(parentFile: VirtualFile): Boolean = {
      if (parentFile == null || !isInsideProjectRoot(parentFile)) return true

      val parentFileModule = getModuleFromVirtualFile(parentFile)(project)
      val canProcessParentFileModule = parentFileModule != null && !isSourceSetModule(parentFileModule)
      if (canProcessParentFileModule) {
        // If adding a source set suffix to the parent module's name results in the module's name,
        // it indicates that the parent module is the correct parent for the module being verified
        s"${parentFileModule.getName}.$sourceSetSuffix" == moduleName
      } else {
        isFileTheCorrectParent(parentFile.getParent)
      }
    }

    isFileTheCorrectParent(virtualFile.getParent)
  }

  private def isSourceSetModule(module: Module): Boolean = {
    val externalModuleType = ExternalSystemApiUtil.getExternalModuleType(module)
    externalModuleType == SbtModuleType.sbtSourceSetModuleType
  }

  private def isRootModuleInMultiBuildProject(module: Module, project: Project, virtualFile: VirtualFile): Boolean = {
    val regexPattern = (path: String) => {
      val quoted = Pattern.quote(path)
      s""".*$quoted(?:/)?\\]""".r
    }
    val moduleRegexPattern = regexPattern(virtualFile.getPath)

    def moduleIdOpt(module: Module): Option[String] = Option(ExternalSystemApiUtil.getExternalProjectId(module))

    def isRootAndBelongsToDifferentBuild(module: Module): Boolean =
      moduleIdOpt(module).exists { id =>
        val moduleRootPath = ExternalSystemApiUtil.getExternalProjectPath(module)
        if (moduleRootPath == null) false
        else {
          val isRoot = regexPattern(moduleRootPath).matches(id)
          isRoot && !moduleRegexPattern.matches(id)
        }
      }

    moduleIdOpt(module).exists { id =>
      val isRootProject = moduleRegexPattern.matches(id)
      isRootProject && {
        // note: checking if there are more root projects and if they belong to another build
        val modules = ModuleManager.getInstance(project).getModules
        modules.exists(isRootAndBelongsToDifferentBuild)
      }
    }
  }
}

/**
 * A wrapper class over [[ProjectViewModuleGroupNode]] that keeps information about
 * top level [[PsiDirectoryNode]] files within the [[ProjectViewProjectNode]].
 *
 * This information is necessary during the processing of [[ProjectViewModuleGroupNode]] children
 * to prevent duplicate directory displays in the project view.
 *
 * @see [[https://youtrack.jetbrains.com/issue/SCL-24041/When-a-source-directory-is-located-outside-the-project-root-the-outside-grouping-node-displays-duplicated-directories]]
 */
 private case class ScalaProjectViewModuleGroupNode(
  project: Project,
  moduleGroup: ModuleGroup,
  settings: ViewSettings,
  projectViewTopLevelDirectories: Seq[VirtualFile]
) extends ProjectViewModuleGroupNode(project, moduleGroup, settings)

private case class ScalaModuleDirectoryNode(
  project: Project,
  psiDirectory: PsiDirectory,
  settings: ViewSettings,
  @NlsSafe moduleShortName: String,
  @Nullable filter: PsiFileSystemItemFilter,
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
