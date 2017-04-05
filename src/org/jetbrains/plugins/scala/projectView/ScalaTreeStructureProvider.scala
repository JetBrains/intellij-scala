package org.jetbrains.plugins.scala.projectView

import java.util

import com.intellij.ide.projectView.{TreeStructureProvider, ViewSettings}
import com.intellij.openapi.project.DumbAware
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
    children.asScala.map(transform(_, settings)).asJavaCollection
}

private object ScalaTreeStructureProvider {
  private def transform(node: Node, settings: ViewSettings): Node = node.getValue match {
    case file: ScalaFile => nodeFor(file, settings)

    case definition: ScTypeDefinition if !node.isInstanceOf[TypeDefinitionNode] =>
      new TypeDefinitionNode(node.getProject, definition, settings)

    case _ => node
  }

  private def nodeFor(file: ScalaFile, settings: ViewSettings): Node = file match {
    case ScriptFile() => new FileNode(file, settings)

    case SingularDefinition(definition) =>
      new TypeDefinitionNode(file.getProject, definition, settings)

    case PackageObject(definition) =>
      new SingularDefinitionNode(file.getProject, definition, Icons.PACKAGE_OBJECT, settings)

    case ClassAndCompanionObject(classDefinition, _) if !settings.isShowMembers =>
      new SingularDefinitionNode(file.getProject, classDefinition, Icons.CLASS_AND_OBJECT, settings)

    case TraitAndCompanionObject(traitDefinition, _) if !settings.isShowMembers =>
      new SingularDefinitionNode(file.getProject, traitDefinition, Icons.TRAIT_AND_OBJECT, settings)

    case _ => new FileNode(file, settings)
  }
}
