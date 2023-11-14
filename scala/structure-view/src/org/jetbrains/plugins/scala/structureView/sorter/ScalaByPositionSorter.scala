package org.jetbrains.plugins.scala.structureView.sorter

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, Sorter}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.structureView.element.Element

import java.util.Comparator

object ScalaByPositionSorter extends Sorter() {
  @NonNls val ID = "ACTUAL_ORDER_SORTER"

  override val getName: String = ID

  override val isVisible: Boolean = false

  override def getPresentation: ActionPresentation =
    new ActionPresentationData("Sort.actually", "Sort By Position", AllIcons.ObjectBrowser.Sorted)

  override def getComparator: Comparator[_] = (o1: AnyRef, o2: AnyRef) => (o1, o2) match {
    case (e1: Element, e2: Element) if !e1.inherited && !e2.inherited =>
      e1.element.getTextOffset - e2.element.getTextOffset
    case _ => 0
  }
}
