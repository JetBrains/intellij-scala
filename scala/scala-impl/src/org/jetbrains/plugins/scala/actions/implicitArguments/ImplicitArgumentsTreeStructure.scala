package org.jetbrains.plugins.scala.actions.implicitArguments

import java.util

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.{AbstractTreeNode, AbstractTreeStructure, NodeDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.actions.implicitArguments.ImplicitArgumentNodes.resolveResultNode
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.JavaConverters.asJavaCollectionConverter

class ImplicitArgumentsTreeStructure(project: Project,
                                     results: Seq[ScalaResolveResult])
  extends AbstractTreeStructure {

  override def getRootElement: AnyRef = new RootNode

  override def getParentElement(p1: Any): AnyRef = null

  override def getChildElements(p1: Any): Array[AnyRef] = {
    p1 match {
      case n: ImplicitParametersNodeBase =>
        val childrenImpl = n.getChildren
        childrenImpl.toArray(new Array[AnyRef](childrenImpl.size))
      case rn: RootNode => rn.getChildren.toArray
      case _ => Array.empty
    }
  }

  override def createDescriptor(obj: Any, parent: NodeDescriptor[_]): NodeDescriptor[_] = {
    obj.asInstanceOf[NodeDescriptor[_]]
  }

  override def hasSomethingToCommit: Boolean = false

  override def commit(): Unit = {}

  private class RootNode extends AbstractTreeNode[Any](project, ()) {
    override def getChildren: util.Collection[_ <: AbstractTreeNode[_]] =
      results.map(resolveResultNode).asJavaCollection

    override def update(presentation: PresentationData): Unit = {}
  }

}

