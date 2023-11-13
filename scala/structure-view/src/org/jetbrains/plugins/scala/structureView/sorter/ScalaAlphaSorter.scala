package org.jetbrains.plugins.scala.structureView.sorter

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, Sorter, SorterUtil}
import com.intellij.openapi.editor.PlatformEditorBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.structureView.element.Test

import java.util.Comparator

object ScalaAlphaSorter extends Sorter() {
  @NonNls val ID = "ALPHA_SORTER_IGNORING_TEST_NODES"

  override val getName: String = ID

  override val isVisible: Boolean = true

  // TODO move to the implementation of testing support
  override def getComparator: Comparator[_] =
    (o1: AnyRef, o2: AnyRef) => (o1, o2) match {
      case (_: Test, _: Test) => 0
      case (_, _: Test) => -1
      case (_: Test, _) => 1
      case _ => SorterUtil.getStringPresentation(o1).compareToIgnoreCase(SorterUtil.getStringPresentation(o2))
    }

  override def getPresentation: ActionPresentation = {
    val sortAlphabetically = PlatformEditorBundle.message("action.sort.alphabetically")
    new ActionPresentationData(
      sortAlphabetically,
      sortAlphabetically,
      AllIcons.ObjectBrowser.Sorted
    )
  }
}
