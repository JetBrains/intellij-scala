package org.jetbrains.plugins.scala.projectView

import java.util

import com.intellij.ide.projectView.{TreeStructureProvider, ViewSettings}
import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.projectView.ScalaTreeStructureProvider._

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
class ScalaTreeStructureProvider extends TreeStructureProvider with DumbAware {
  override def modify(parent: Node, children: util.Collection[Node], settings: ViewSettings): util.Collection[Node] =
    children.asScala
      .map(it => transform(it)(it.getProject, settings))
      .asJavaCollection
}

private object ScalaTreeStructureProvider {
  private def transform(node: Node)(implicit project: Project, settings: ViewSettings): Node = node.getValue match {
    case file: ScalaFile => nodeFor(file)

    case definition: ScTypeDefinition if !node.isInstanceOf[TypeDefinitionNode] =>
      new TypeDefinitionNode(definition)

    case _ => node
  }

  private def nodeFor(file: ScalaFile)(implicit project: Project, settings: ViewSettings): Node = file match {
    case WorksheetFile() | ScriptFile() => new FileNode(file)

    case ScalaDialectFile() => new FileNode(file)

    case SingularDefinition(definition) => new TypeDefinitionNode(definition)

    case ClassAndCompanionObject(classDefinition, _) if !settings.isShowMembers =>
      new CustomDefinitionNode(classDefinition, Icons.CLASS_AND_OBJECT)

    case TraitAndCompanionObject(traitDefinition, _) if !settings.isShowMembers =>
      new CustomDefinitionNode(traitDefinition, Icons.TRAIT_AND_OBJECT)

    case _ => new FileNode(file)
  }
}
