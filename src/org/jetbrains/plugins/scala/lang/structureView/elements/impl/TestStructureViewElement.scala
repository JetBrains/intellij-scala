package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl.TestItemRepresentation
/**
 * @author Roman.Shein
 * @since 09.04.2015.
 */
class TestStructureViewElement(override protected val myElement: PsiElement, protected val testName: String,
                                          protected val myChildren: Array[TreeElement] = Array[TreeElement](),
                                          val testStatus: Int = TestStructureViewElement.normalStatusId)
    extends ScalaStructureViewElement(myElement, false) {

  override def getChildren: Array[TreeElement] = myChildren

  override def getPresentation: ItemPresentation = {
    new TestItemRepresentation(myElement, testName, testStatus)
  }
}

object TestStructureViewElement {
  val normalStatusId = 1
  val ignoredStatusId = 2
  val pendingStatusId = 3
}