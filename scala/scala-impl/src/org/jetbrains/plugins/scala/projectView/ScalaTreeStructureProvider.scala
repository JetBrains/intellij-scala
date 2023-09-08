package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.canRealModuleNameBeHidden
import com.intellij.ide.projectView.impl.nodes.{PsiDirectoryNode, PsiFileSystemItemFilter}
import com.intellij.ide.projectView.{PresentationData, TreeStructureProvider, ViewSettings}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.ui.SimpleTextAttributes.{REGULAR_BOLD_ATTRIBUTES, merge}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.ExternalSystemUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem

import scala.jdk.CollectionConverters._
import java.util

final class ScalaTreeStructureProvider extends TreeStructureProvider with DumbAware {

  import ScalaTreeStructureProvider._

  override def modify(parent: Node, children: util.Collection[Node], settings: ViewSettings): util.Collection[Node] =
    children.asScala.map { it =>
      transform(it, children)(it.getProject, settings)
    }.asJavaCollection
}

private object ScalaTreeStructureProvider {

  private def transform(node: Node, children: util.Collection[Node])
                       (implicit project: Project, settings: ViewSettings) = {
    val nodeValue = node.getValue
    nodeValue match {
      case psiDirectory: PsiDirectory =>
        val psiDirectoryNodeOpt = children.asScala.toSeq
          .filter(_.isInstanceOf[PsiDirectoryNode])
          .find(_.asInstanceOf[PsiDirectoryNode].getVirtualFile == psiDirectory.getVirtualFile)
        psiDirectoryNodeOpt match {
          case Some(node) =>
            node
            //getScalaModuleDirectoryNode(node.asInstanceOf[PsiDirectoryNode]).getOrElse(node)
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

  private def getScalaModuleDirectoryNode(directoryNode: PsiDirectoryNode)
                                         (implicit project: Project, settings: ViewSettings): Option[ScalaModuleDirectoryNode] = {
    val psiDirectory = directoryNode.getValue
    if (psiDirectory == null) return None
    val virtualFile = psiDirectory.getVirtualFile
    if (!ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) return None
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    for {
      module <- Option(fileIndex.getModuleForFile(virtualFile))
      moduleId <- Option(ExternalSystemApiUtil.getExternalProjectId(module))
      moduleDataNode <- ExternalSystemUtil.getModuleDataNode(SbtProjectSystem.Id, project, moduleId).toOption
      moduleData = moduleDataNode.getData
      isRootModule = Option(moduleData.getProperty(Sbt.moduleDataKeyForProjectURI)).contains(moduleData.getLinkedExternalProjectPath)
      if !isRootModule
    } yield {
      ScalaModuleDirectoryNode(project, psiDirectory, settings, module, moduleData.getInternalName, moduleData.getModuleName, directoryNode.getFilter)
    }
  }

}

case class ScalaModuleDirectoryNode(project: Project, psiDirectory: PsiDirectory, settings: ViewSettings, module: Module, internalModuleName: String, moduleShortName: String, filter: PsiFileSystemItemFilter)
  extends PsiDirectoryNode(project, psiDirectory, settings, filter) {

  private val directoryName = psiDirectory.getVirtualFile.getName
  private val appendShortModuleName = compareDirectoryNameWithModule(moduleShortName)
  override def shouldShowModuleName(): Boolean = canRealModuleNameBeHidden

  private def compareDirectoryNameWithModule(name: String): Boolean =
    !StringUtil.equalsIgnoreCase(name.replace("-", ""),
      directoryName.replace("-", ""))

  override def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)
    if (!canRealModuleNameBeHidden) {
      if (appendShortModuleName) {
        data.addText("[" + moduleShortName + "]", REGULAR_BOLD_ATTRIBUTES)
      } else {
          val fragments = data.getColoredText
          if (fragments.size == 1) {
            val fragment = fragments.iterator.next
            data.clearText()
            data.addText(fragment.getText.trim, merge(fragment.getAttributes, REGULAR_BOLD_ATTRIBUTES))
          }
      }
    }
  }
}
