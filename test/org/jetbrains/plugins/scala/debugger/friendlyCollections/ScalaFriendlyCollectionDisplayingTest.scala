package org.jetbrains.plugins.scala.debugger.friendlyCollections

import java.util

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.impl.FrameVariablesTree
import com.intellij.debugger.ui.impl.watch.{DebuggerTree, LocalVariableDescriptorImpl, NodeDescriptorImpl}
import com.intellij.debugger.ui.tree.render.{ClassRenderer, ArrayRenderer, ChildrenBuilder, DescriptorLabelListener}
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptorFactory, NodeManager, ValueDescriptor}
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase
import org.jetbrains.plugins.scala.debugger.ui.ListLikeCollectionNodeRenderer

/**
 * User: Dmitry Naydanov
 * Date: 9/5/12
 */
class ScalaFriendlyCollectionDisplayingTest extends ScalaDebuggerTestCase {
  private val COMMON_FILE_NAME = "dummy.scala"
  private val UNIQUE_ID = "uniqueID"

  private def getStringRep(variableName: String): (String, List[String]) = {
    import scala.collection.JavaConversions._

    val frameTree = new FrameVariablesTree(getProject)
    var testVariableChildren: util.List[DebuggerTreeNode] = null

    val testVariable = managed[LocalVariableDescriptorImpl] {
      val context = evaluationContext()
      val testVariable = localVar(frameTree, context, variableName)
      val renderer = new ListLikeCollectionNodeRenderer

      testVariable setRenderer renderer
      testVariable.updateRepresentation(context, DescriptorLabelListener.DUMMY_LISTENER)
      renderer.buildChildren(testVariable calcValue context, new ChildrenBuilder {
        def setChildren(children: util.List[DebuggerTreeNode]) {testVariableChildren = children}

        def getDescriptorManager: NodeDescriptorFactory = frameTree.getNodeFactory

        def getNodeManager: NodeManager = frameTree.getNodeFactory

        def setRemaining(remaining: Int) {}

        def initChildrenArrayRenderer(renderer: ArrayRenderer) {}

        def getParentDescriptor: ValueDescriptor = testVariable
      }, context)

      testVariable
    }

    managed{testVariableChildren map (_.getDescriptor) foreach {
      case impl: NodeDescriptorImpl =>
        impl.updateRepresentation(evaluationContext(), DescriptorLabelListener.DUMMY_LISTENER)
      case a => println(a)
    }}

    //<magic>
    evalResult(variableName)
    //</magic> 

    (testVariable.getLabel, (testVariableChildren map {_.getDescriptor.getLabel}).toList)
  }

  private def localVar(frameTree: DebuggerTree, evaluationContext: EvaluationContextImpl, name: String) = {
    try {
      val frameProxy = evaluationContext.getFrameProxy
      val local = frameTree.getNodeFactory.getLocalVariableDescriptor(null, frameProxy visibleVariableByName name)
      local setContext evaluationContext
      local
    } catch {
      case e: EvaluateException => null
    }
  }

  protected def genericWatchTest(fileText: String, breakpointPos: Int, collectionName: String,
                                 collectionLength: Int, collectionClass: String) {
    import junit.framework.Assert._
    addFileToProject(COMMON_FILE_NAME, fileText)
    addBreakpoint(COMMON_FILE_NAME, breakpointPos)
    runDebugger("Main"){
      waitForBreakpoint()
      val (label, children) = getStringRep(collectionName)
      val classRenderer: ClassRenderer = NodeRendererSettings.getInstance().getClassRenderer
      val typeName = classRenderer.renderTypeName(collectionClass)
      val expectedLabel = s"$collectionName = {$typeName@$UNIQUE_ID}${
        ListLikeCollectionNodeRenderer.transformName(collectionClass)} size = $collectionLength"

      assertEquals(expectedLabel, label)
      assertEquals(children.size, collectionLength)
      val intType = classRenderer.renderTypeName("java.lang.Integer")
      val intLabel = s"{$intType@$UNIQUE_ID}"

      var testIndex = 0
      children foreach { childLabel =>
        val expectedChildLabel = s"($testIndex)  = $intLabel${testIndex + 1}"//compiler bug?

        assertEquals(childLabel, expectedChildLabel)
        testIndex += 1
      }
    }
  }

  def testList() {
    genericWatchTest(
      """
        |object Main {
        |  def main(args: Array[String]) {
        |    val lst = List(1, 2, 3, 4, 5, 6) 
        |    val a = 1 //3 - bp here
        |  }
        |}  
      """.replace("\r", "").stripMargin.trim, 3, "lst", 6, "scala.collection.immutable.$colon$colon")
  }

  def testStack() {
    genericWatchTest(
      """
        |object Main {
        |  def main(args: Array[String]) {
        |    import scala.collection.mutable
        |    val stack = mutable.Stack(1,2,3,4,5,6,7,8)
        |    val b = 45//4 - bp here
        |  }
        |}
      """.stripMargin.replace("\r","").trim, 4, "stack", 8, "scala.collection.mutable.Stack")
  }

  def testMutableList() {
    genericWatchTest(
    """
      |object Main {
      |  def main(args: Array[String]) {
      |    val mutableList = scala.collection.mutable.MutableList(1,2,3,4,5)
      |    val a = 1//3 - bp here
      |  }
      |}
    """.stripMargin.replace("\r", "").trim, 3, "mutableList", 5, "scala.collection.mutable.MutableList")
  }

  def testQueue() {
    genericWatchTest(
      """
        |object Main {
        |  def main(args: Array[String]) {
        |    val queue = scala.collection.immutable.Queue(1,2,3,4)
        |    val a = 1//3 - bp here
        |  }
        |}
      """.stripMargin.replace("\r", "").trim, 3, "queue", 4, "scala.collection.immutable.Queue")
  }

  def testLongList() {
    genericWatchTest(
      """
        |object Main {
        |  def main(args: Array[String]) {
        |    val longList = (1 to 50).toList
        |    val a = 1//3 - bp here
        |  }
        |}
      """.stripMargin.replace("\r", "").trim, 3, "longList", 50, "scala.collection.immutable.$colon$colon")
  }
}
