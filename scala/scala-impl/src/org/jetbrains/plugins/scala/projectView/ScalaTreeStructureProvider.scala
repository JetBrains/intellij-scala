package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.{TreeStructureProvider, ViewSettings}
import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

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
}
