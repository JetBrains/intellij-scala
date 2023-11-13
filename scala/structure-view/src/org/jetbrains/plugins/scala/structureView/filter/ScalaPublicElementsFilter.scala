package org.jetbrains.plugins.scala.structureView.filter

import com.intellij.ide.structureView.impl.java.JavaClassTreeElementBase
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, Filter, TreeElement}
import com.intellij.ui.{IconManager, PlatformIcons}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewBundle
import org.jetbrains.plugins.scala.structureView.element.AbstractAccessLevelProvider

object ScalaPublicElementsFilter extends Filter {
  @NonNls val ID = "SCALA_SHOW_NON_PUBLIC"

  override val getName: String = ID

  override val isReverted: Boolean = true

  override def isVisible(treeNode: TreeElement): Boolean = treeNode match
    case node: AbstractAccessLevelProvider => node.isPublic
    case node: JavaClassTreeElementBase[_] => node.isPublic
    case _ => true

  override def getPresentation: ActionPresentation =
    ActionPresentationData(
      ScalaStructureViewBundle.message("show.non.public"),
      null,
      IconManager.getInstance().getPlatformIcon(PlatformIcons.Private)
    )
}
