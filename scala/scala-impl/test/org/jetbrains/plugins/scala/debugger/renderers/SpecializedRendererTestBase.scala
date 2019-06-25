package org.jetbrains.plugins.scala
package debugger
package renderers

import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.NodeDescriptor
import org.junit.Assert
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 14-Mar-17
  */
@Category(Array(classOf[DebuggerTests]))
class SpecializedRenderer_until_2_11 extends SpecializedRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= Scala_2_11
}
@Category(Array(classOf[DebuggerTests]))
class SpecializedRenderer_since_2_12 extends SpecializedRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_12
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
