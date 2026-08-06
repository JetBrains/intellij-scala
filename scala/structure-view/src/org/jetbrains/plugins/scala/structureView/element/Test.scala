package org.jetbrains.plugins.scala.structureView.element

import com.intellij.execution.testframework.PoolOfTestIcons.{IGNORED_ICON, NOT_RAN}
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing.Icon

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

  override def getAccessLevel: Int = PsiUtil.ACCESS_LEVEL_PUBLIC
}

object Test {
  // TODO: custom type, add icon SCL-15735
  val NormalStatusId = 1
  val IgnoredStatusId = 2
  val PendingStatusId = 3
}
