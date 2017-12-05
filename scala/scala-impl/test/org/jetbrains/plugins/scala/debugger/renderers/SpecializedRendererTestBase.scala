package org.jetbrains.plugins.scala.debugger.renderers

import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.{FieldDescriptor, NodeDescriptor}
import org.jetbrains.plugins.scala.{DebuggerTests, SlowTests}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11, Scala_2_12}
import org.junit.Assert
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 14-Mar-17
  */
@Category(Array(classOf[DebuggerTests]))
class SpecializedRenderer_211 extends SpecializedRendererTestBase {
  override implicit val version: ScalaVersion = Scala_2_11
}
@Category(Array(classOf[DebuggerTests]))
class SpecializedRenderer_212 extends SpecializedRendererTestBase {
  override implicit val version: ScalaVersion = Scala_2_12
}

abstract class SpecializedRendererTestBase extends RendererTestBase {

  private def calcName(nodeDescriptor: NodeDescriptor) = nodeDescriptor match {
    case vd: ValueDescriptorImpl => vd.calcValueName()
    case _ => nodeDescriptor.getName
  }

  private def checkChildrenNames(varName: String, childrenNames: Seq[String]) = {
    runDebugger() {
      waitForBreakpoint()
      val (_, names) = renderLabelAndChildren(varName, calcName)
      Assert.assertEquals(childrenNames.sorted, names.sorted)
    }
  }

  addFileWithBreakpoints("SpecializedTuple.scala",
  s"""object SpecializedTuple {
    |  def main(args: Array[String]): Unit = {
    |    val x = (1, 2)
    |    "stop"$bp
    |  }
    |}
  """.stripMargin)
  def testSpecializedTuple(): Unit = {
    checkChildrenNames("x", List("_1", "_2"))
  }
}
