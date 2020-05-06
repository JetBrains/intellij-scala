package org.jetbrains.plugins.scala.testingSupport

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.smartTree.{TreeElement, TreeElementWrapper}
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.lang.structureView.element.Test
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions._
import org.junit.Assert._

import scala.collection.JavaConverters._

trait FileStructureTest {

  protected def buildFileStructure(fileName: String): TreeElementWrapper

  protected def runFileStructureViewTest(testClassName: String, status: Int, tests: String*): Unit

  protected def runFileStructureViewTest(testClassName: String, testName: String, parentTestName: Option[String] = None, testStatus: Int = Test.NormalStatusId): Unit

  protected def assertTestNodeInFileStructure(root: TreeElementWrapper, nodeName: String, parentName: Option[String], status: Int): Unit = {

    def doAssert(node: AbstractTreeNode[_], currentParentName: String): Unit = {
      node.getValue match {
        case testElement: Test =>
          val presentation = testElement.getPresentation
          assertIsA[Test](presentation)
          assertEquals("node text", nodeName, presentation.getPresentableText)
          assertEquals("node status", status, presentation.asInstanceOf[Test].testStatus)
          parentName.foreach(pn => assertEquals("parent name", pn, currentParentName))
        case _ =>
          node.getChildren.asScala.foreach { child =>
            doAssert(child, node.getValue.asInstanceOf[TreeElement].getPresentation.getPresentableText)
          }
      }
    }

    try {
      EdtTestUtil.runInEdtAndWait(() => doAssert(root, ""))
    } catch {
      case ae: AssertionError =>
        val parentStr = parentName.fold("")(p => s"with parent 'p'")
        throw new AssertionError(s"test node for test '$nodeName'$parentStr was not in file structure for root '$root'", ae)
    }
  }
}
