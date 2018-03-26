package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import javax.swing.Icon

import com.intellij.execution.testframework.PoolOfTestIcons.{IGNORED_ICON, NOT_RAN}
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement.Presentation
/**
 * @author Roman.Shein
 * @since 09.04.2015.
 */
// TODO move to the implemenation of testing support
class TestStructureViewElement(elem: PsiElement,
                               testName: String,
                               myChildren: Array[TreeElement] = TreeElement.EMPTY_ARRAY,
                               testStatus: Int = TestStructureViewElement.NormalStatusId) extends ScalaStructureViewElement(elem, false) {

  override def getChildren: Array[TreeElement] = myChildren

  override def getPresentation: ItemPresentation = new Presentation(elem, testName, testStatus)
}

object TestStructureViewElement {
  // TOOD custom type
  val NormalStatusId = 1
  val IgnoredStatusId = 2
  val PendingStatusId = 3

  class Presentation(element: PsiElement, val testName: String, val testStatus: Int) extends ScalaItemPresentation(element) {
    override def getPresentableText: String = testName

    override def getIcon(open: Boolean): Icon = testStatus match {
      case TestStructureViewElement.IgnoredStatusId => IGNORED_ICON
      case TestStructureViewElement.PendingStatusId => NOT_RAN
      case _ => Icons.SCALA_TEST_NODE
    }
  }
}