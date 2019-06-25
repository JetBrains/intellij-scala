package org.jetbrains.plugins.scala
package debugger
package renderers

import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render._
import org.jetbrains.plugins.scala.debugger.ui.ScalaCollectionRenderer
import org.junit.experimental.categories.Category

/**
 * User: Dmitry Naydanov
 * Date: 9/5/12
 */
@Category(Array(classOf[DebuggerTests]))
class ScalaCollectionRendererTest_until_2_11 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= Scala_2_11
}
@Category(Array(classOf[DebuggerTests]))
class ScalaCollectionRendererTest_since_2_12 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_12

  addFileWithBreakpoints("Lazy.scala",
    s"""
       |object Lazy {
       |  def main(args: Array[String]) {
       |    val stream = Stream.from(42)
       |    val a = 1$bp
       |  }
       |}
      """.replace("\r", "").stripMargin.trim
  )

  def testLazy() {
    testLazyCollectionRendering("stream", "scala.collection.immutable.Stream$Cons", "Stream(42, ?)")
  }
}
@Category(Array(classOf[DebuggerTests]))
class ScalaCollectionRendererTest_since_2_13 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13

  addFileWithBreakpoints("Lazy.scala",
    s"""
       |object Lazy {
       |  def main(args: Array[String]) {
       |    val list = LazyList.from(1)
       |    val stream = Stream.from(1)
       |    val a = 1$bp
       |    val b = 2$bp
       |  }
       |}
      """.replace("\r", "").stripMargin.trim
  )
  def testLazy() {
    testLazyCollectionRendering("list", "scala.collection.immutable.LazyList", "LazyList(<not computed>)")
    testLazyCollectionRendering("stream", "scala.collection.immutable.Stream$Cons", "Stream(1, <not computed>)")
  }
}

abstract class ScalaCollectionRendererTestBase extends RendererTestBase {
  private val UNIQUE_ID = "uniqueID"

  protected def testCollectionRenderer(collectionName: String,
                                       collectionClass: String,
                                       afterTypeLabel: String,
                                       checkChildren: Boolean): Unit = {
    import org.junit.Assert._
    runDebugger() {
      waitForBreakpoint()
      val (label, children) = renderLabelAndChildren(collectionName)
      val classRenderer: ClassRenderer = NodeRendererSettings.getInstance().getClassRenderer
      val typeName = classRenderer.renderTypeName(collectionClass)
      val expectedLabel = s"$collectionName = {$typeName@$UNIQUE_ID}$afterTypeLabel"

      assertEquals(expectedLabel, label)

      if (checkChildren) {
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
  }

  protected def testScalaCollectionRenderer(collectionName: String, collectionLength: Int, collectionClass: String): Unit = {
    val shortClassName = ScalaCollectionRenderer.transformName(collectionClass)
    val afterTypeLabel = s"$shortClassName size = $collectionLength"
    testCollectionRenderer(collectionName, collectionClass, afterTypeLabel, checkChildren = true)
  }


  protected def testLazyCollectionRendering(collectionName: String, collectionClass: String, afterTypeLabel: String): Unit =
    testCollectionRenderer(collectionName, collectionClass, afterTypeLabel, checkChildren = false)

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

  addFileWithBreakpoints("ListBuffer.scala",
    s"""
       |object MutableList {
       |  def main(args: Array[String]) {
       |    val mutableList = scala.collection.mutable.ListBuffer(1,2,3,4,5)
       |    val a = 1$bp
       |  }
       |}
    """.stripMargin.replace("\r", "").trim
  )
  def testMutableList() {
    testScalaCollectionRenderer("mutableList", 5, "scala.collection.mutable.ListBuffer")
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
