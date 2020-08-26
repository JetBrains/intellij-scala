package org.jetbrains.plugins.scala.testingSupport

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.smartTree.{TreeElement, TreeElementWrapper}
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.lang.structureView.element.Test
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions._
import org.junit.Assert._

import scala.jdk.CollectionConverters._

trait FileStructureTest {

  protected def buildFileStructure(fileName: String): TreeElementWrapper

  protected def runFileStructureViewTest(testClassName: String, status: Int, tests: String*): Unit

  protected def runFileStructureViewTest(testClassName: String, testName: String, parentTestName: Option[String] = None, testStatus: Int = Test.NormalStatusId): Unit

  protected def assertTestNodeInFileStructure(
    root: TreeElementWrapper,
    expectedNodeName: String,
    expectedParentName: Option[String],
    expectedStatus: Int
  ): Unit = {
    def containsNodeWithName(currentNode: AbstractTreeNode[_], currentParentName: String): Boolean = {
      val treeElement = currentNode.getValue.asInstanceOf[TreeElement]
      val nodeName = treeElement.getPresentation.getPresentableText
      val matches = treeElement match {
        case test: Test =>
          expectedNodeName == nodeName &&
            expectedStatus == test.testStatus &&
            expectedParentName.forall(_ == currentParentName)
        case _ =>
          false
      }
      matches ||
        currentNode.getChildren.asScala.exists(containsNodeWithName(_, nodeName))
    }

    EdtTestUtil.runInEdtAndWait { () =>
      val containsNode = containsNodeWithName(root, "")
      if (!containsNode) {
        val parentStr = expectedParentName.fold("")(p => s" with parent '$p'")
        val allPaths = allAvailablePaths(root)
        val allPathsText = allPaths.map(pathString).mkString("\n")
        fail(s"test node for test '$expectedNodeName'$parentStr was not found in file structure\navailable paths:\n$allPathsText")
      }
    }
  }

  private def allAvailablePaths(root: AbstractTreeNode[_]): Seq[Seq[String]] = {
    def inner(node: AbstractTreeNode[_], curPath: List[String]): Seq[Seq[String]] = {
      val children = node.getChildren
      val path = nodeString(node.getValue.asInstanceOf[TreeElement]) :: curPath
      if (children.isEmpty)
        path :: Nil
      else
        children.asScala.toSeq.flatMap(inner(_, path))
    }
    val result = inner(root, Nil)
    result.map(_.reverse).sortBy(_.mkString)
  }

  private def nodeString(node: TreeElement): String = {
    node match {
      case test: Test =>
        val presentation = test.getPresentation
        val text = presentation.getPresentableText
        val status = presentation.asInstanceOf[Test].testStatus
        s"[$status] $text"
      case _ =>
        node.getPresentation.getPresentableText
    }
  }

  private def pathString(names: Iterable[String]) =
    names.mkString(" / ")
}
