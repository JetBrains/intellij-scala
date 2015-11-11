package org.jetbrains.plugins.scala.debugger.friendlyCollections

import java.util

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch._
import com.intellij.debugger.ui.tree.render._
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptorFactory, NodeManager, ValueDescriptor}
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.debugger.ui.ScalaCollectionRenderer
import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11}

/**
 * User: Dmitry Naydanov
 * Date: 9/5/12
 */
class ScalaCollectionRendererTest extends ScalaDebuggerTestCase with ScalaVersion_2_11 {
  private val COMMON_FILE_NAME = "dummy.scala"
  private val UNIQUE_ID = "uniqueID"

  private def renderLabelAndChildren(variableName: String): (String, List[String]) = {
    import scala.collection.JavaConversions._

    val frameTree = new ThreadsDebuggerTree(getProject)
    Disposer.register(getTestRootDisposable, frameTree)
    var testVariableChildren: util.List[DebuggerTreeNode] = null

    val testVariable = managed[LocalVariableDescriptorImpl] {
      val context = evaluationContext()
      val testVariable = localVar(frameTree, context, variableName)
      val renderer = testVariable.getRenderer(getDebugProcess)
      testVariable.setRenderer(renderer)
      testVariable.updateRepresentation(context, DescriptorLabelListener.DUMMY_LISTENER)
      val value = testVariable.calcValue(context)
      renderer.buildChildren(value, new ChildrenBuilder {
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

    managed {
      (testVariable.getLabel, (testVariableChildren map {_.getDescriptor.getLabel}).toList)
    }
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

  protected def testScalaCollectionRenderer(collectionName: String, collectionLength: Int, collectionClass: String) = {
    import junit.framework.Assert._
    runDebugger() {
      waitForBreakpoint()
      val (label, children) = renderLabelAndChildren(collectionName)
      val classRenderer: ClassRenderer = NodeRendererSettings.getInstance().getClassRenderer
      val typeName = classRenderer.renderTypeName(collectionClass)
      val expectedLabel = s"$collectionName = {$typeName@$UNIQUE_ID}${
        ScalaCollectionRenderer.transformName(collectionClass)} size = $collectionLength"

      assertEquals(expectedLabel, label)
      val intType = classRenderer.renderTypeName("java.lang.Integer")
      val intLabel = s"{$intType@$UNIQUE_ID}"

      var testIndex = 0
      children foreach { childLabel =>
        val expectedChildLabel = s"$testIndex = $intLabel${testIndex + 1}"

        assertEquals(childLabel, expectedChildLabel)
        testIndex += 1
      }
    }
  }

  addFileWithBreakpoints("ShortList.scala",
    s"""
       |object ShortList {
       |  def main(args: Array[String]) {
       |    val lst = List(1, 2, 3, 4, 5, 6)
       |    val a = 1$bp
       |  }
       |}
      """.replace("\r", "").stripMargin.trim
  )
  def testShortList() {
    testScalaCollectionRenderer("lst", 6, "scala.collection.immutable.$colon$colon")
  }


  addFileWithBreakpoints("Stack.scala",
    s"""
       |object Stack {
       |  def main(args: Array[String]) {
       |    import scala.collection.mutable
       |    val stack = mutable.Stack(1,2,3,4,5,6,7,8)
       |    val b = 45$bp
       |  }
       |}
      """.stripMargin.replace("\r","").trim
  )
  def testStack() {
    testScalaCollectionRenderer("stack", 8, "scala.collection.mutable.Stack")
  }

  addFileWithBreakpoints("MutableList.scala",
    s"""
       |object MutableList {
       |  def main(args: Array[String]) {
       |    val mutableList = scala.collection.mutable.MutableList(1,2,3,4,5)
       |    val a = 1$bp
       |  }
       |}
    """.stripMargin.replace("\r", "").trim
  )
  def testMutableList() {
    testScalaCollectionRenderer("mutableList", 5, "scala.collection.mutable.MutableList")
  }

  addFileWithBreakpoints("Queue.scala",
    s"""
       |object Queue {
       |  def main(args: Array[String]) {
       |    val queue = scala.collection.immutable.Queue(1,2,3,4)
       |    val a = 1$bp
       |  }
       |}
      """.stripMargin.replace("\r", "").trim
  )
  def testQueue() {
    testScalaCollectionRenderer("queue", 4, "scala.collection.immutable.Queue")
  }

  addFileWithBreakpoints("LongList.scala",
    s"""
       |object LongList {
       |  def main(args: Array[String]) {
       |    val longList = (1 to 50).toList
       |    val a = 1$bp
       |  }
       |}
      """.stripMargin.replace("\r", "").trim
  )
  def testLongList() {
    testScalaCollectionRenderer("longList", 50, "scala.collection.immutable.$colon$colon")
  }
}
