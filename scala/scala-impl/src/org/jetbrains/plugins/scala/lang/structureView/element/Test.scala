package org.jetbrains.plugins.scala.lang.structureView.element

import javax.swing.Icon

import com.intellij.execution.testframework.PoolOfTestIcons.{IGNORED_ICON, NOT_RAN}
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.icons.Icons
/**
 * @author Roman.Shein
 * @since 09.04.2015.
 */
// TODO move to the implemenation of testing support
class Test(elem: PsiElement,
           val testName: String,
           myChildren: Array[TreeElement] = TreeElement.EMPTY_ARRAY,
           val testStatus: Int = Test.NormalStatusId) extends AbstractTreeElement(elem) {

  override def getPresentableText: String = testName

  override def getIcon(open: Boolean): Icon = testStatus match {
    case Test.IgnoredStatusId => IGNORED_ICON
    case Test.PendingStatusId => NOT_RAN
    case _ => Icons.SCALA_TEST_NODE
  }

  override def getChildren: Array[TreeElement] = myChildren

  override def isAlwaysLeaf: Boolean = false
}

object Test {
  // TODO: custom type, add icon SCL-15735
  val NormalStatusId = 1
  val IgnoredStatusId = 2
  val PendingStatusId = 3
}