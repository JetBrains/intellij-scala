package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import javax.swing.Icon

import com.intellij.execution.testframework.PoolOfTestIcons._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation

/**
 * @author Roman.Shein
 * @since 14.04.2015.
 */
class TestItemRepresentation(private val element: PsiElement, val testName: String, val testStatus: Int) extends ScalaItemPresentation(element) {

  override def getPresentableText: String = testName

  override def getIcon(open: Boolean): Icon = testStatus match {
    case TestStructureViewElement.ignoredStatusId => IGNORED_ICON
    case TestStructureViewElement.pendingStatusId => NOT_RAN
    case _ => Icons.SCALA_TEST_NODE
  }
}
