package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.canRealModuleNameBeHidden
import com.intellij.ide.projectView.impl.nodes.{PsiDirectoryNode, PsiFileSystemItemFilter}
import com.intellij.ide.projectView.{PresentationData, TreeStructureProvider, ViewSettings}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule
import com.intellij.openapi.module.{Module, ModuleGrouper}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.sbt.project.SbtProjectSystem

import java.util

final class ScalaTreeStructureProvider extends TreeStructureProvider with DumbAware {

  import ScalaTreeStructureProvider._

  import scala.jdk.CollectionConverters._

  override def modify(parent: Node, children: util.Collection[Node], settings: ViewSettings): util.Collection[Node] =
    children.asScala.map { it =>
      transform(it)(it.getProject, settings)
    }.asJavaCollection
}

private object ScalaTreeStructureProvider {

  private def transform(node: Node)
                       (implicit project: Project, settings: ViewSettings): Node = {
    val nodeValue = node.getValue
    nodeValue match {
      case psiDirectory: PsiDirectory =>
        val virtualFile = psiDirectory.getVirtualFile
        val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
        val module = fileIndex.getModuleForFile(virtualFile)
        // For now in the process of creating modules, a single content root for each module is created and its path is equal to project.base.path (it is the root of the module).
        // In ProjectRootsUtil#isModuleContentRoot it is checked whether the virtualFile is equal to the content root path associated with this virtualFile.
        // In a nutshell, with this we check whether virtualFile is the module root. If it is, there is some probability that we should create
        // ScalaModuleDirectoryNode for this node.
        if (!ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) return node
        val moduleShortName = getModuleShortName(module)
        moduleShortName
          .map(ScalaModuleDirectoryNode(project, psiDirectory, settings, _, node.asInstanceOf[PsiDirectoryNode].getFilter))
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

  private def getModuleShortName(module: Module): Option[String] = {
    if (!isExternalSystemAwareModule(SbtProjectSystem.Id, module)) return None

    val fullModuleName = module.getName

    // note: There may be two instances of ModuleGrouper - ExplicitModuleGrouper and QualifiedNameGrouper.
    // ExplicitModuleGrouper is returned when modules are grouped explicitly with `setIdeModuleGroup`.
    // QualifiedNameGrouper is returned when modules are grouped by prepending internal module name with a group name or in projects where grouping is not used at all.
    val moduleGrouper = ModuleGrouper.instanceFor(module.getProject)
    val shortModuleName = moduleGrouper.getShortenedNameByFullModuleName(fullModuleName)

    // Because of the fact that ExplicitModuleGrouper#getShortenedNameByFullModuleName always returns the original module name and
    // QualifiedNameGrouper#getShortenedNameByFullModuleName also returns the original module name when when grouping is not used at all in the project we can assume that
    // when (shortModuleName == moduleName) it is not needed to create custom ScalaModuleDirectoryNode (so None is returned from this method).
    // For such a case com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.updateImpl will work correctly, because the group name is not present in module name
    if (fullModuleName == shortModuleName || shortModuleName.isBlank) None else Some(shortModuleName)
  }
}

private case class ScalaModuleDirectoryNode(
  project: Project,
  psiDirectory: PsiDirectory,
  settings: ViewSettings,
  moduleShortName: String,
  filter: PsiFileSystemItemFilter
) extends PsiDirectoryNode(project, psiDirectory, settings, filter) {

  private lazy val appendModuleName =
    !StringUtil.equalsIgnoreCase(
      moduleShortName.replace("-", ""),
      psiDirectory.getVirtualFile.getName.replace("-", "")
    )

  override def shouldShowModuleName(): Boolean =
    canRealModuleNameBeHidden || !appendModuleName

  override def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)
    if (appendModuleName && !canRealModuleNameBeHidden) {
      data.addText("[" + moduleShortName + "]", REGULAR_BOLD_ATTRIBUTES)
    }
  }
}
