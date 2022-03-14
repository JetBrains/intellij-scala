package org.jetbrains.plugins.scala
package debugger
package renderers

import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render._
import org.jetbrains.plugins.scala.debugger.ui.ScalaCollectionRenderer
import org.junit.experimental.categories.Category

import scala.concurrent.duration.{Duration, DurationInt}

/**
 * User: Dmitry Naydanov
 * Date: 9/5/12
 */
@Category(Array(classOf[DebuggerTests]))
class ScalaCollectionRendererTest_until_2_11 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version  <= LatestScalaVersions.Scala_2_11
}
@Category(Array(classOf[DebuggerTests]))
class ScalaCollectionRendererTest_since_2_12 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  addFileWithBreakpoints("Lazy.scala",
    s"""
       |object Lazy {
       |  def main(args: Array[String]): Unit = {
       |    val stream = Stream.from(42)
       |    val a = 1$bp
       |  }
       |}
      """.replace("\r", "").stripMargin.trim
  )

  def testLazy(): Unit = {
    testLazyCollectionRendering("stream", "scala.collection.immutable.Stream$Cons", "Stream$Cons size = ?")
  }
}
@Category(Array(classOf[DebuggerTests]))
class ScalaCollectionRendererTest_since_2_13 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  addFileWithBreakpoints("Lazy.scala",
    s"""
       |object Lazy {
       |  def main(args: Array[String]): Unit = {
       |    val list = LazyList.from(1)
       |    val stream = Stream.from(1)
       |    val a = 1$bp
       |    val b = 2$bp
       |  }
       |}
      """.replace("\r", "").stripMargin.trim
  )
  def testLazy(): Unit = {
    testLazyCollectionRendering("list", "scala.collection.immutable.LazyList", "LazyList size = ?")(10.seconds)
    testLazyCollectionRendering("stream", "scala.collection.immutable.Stream$Cons", "Stream$Cons size = ?")(10.seconds)
  }
}

@Category(Array(classOf[DebuggerTests]))
class ScalaCollectionRendererTest_3_0 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0
}

abstract class ScalaCollectionRendererTestBase extends RendererTestBase {
  private val UNIQUE_ID = "uniqueID"

  protected def testCollectionRenderer(collectionName: String,
                                       collectionClass: String,
                                       afterTypeLabel: String,
                                       collectionLength: Int,
                                       checkChildren: Boolean)
                                      (implicit timeout: Duration = DefaultTimeout): Unit = {
    import org.junit.Assert._
    runDebugger() {
      waitForBreakpoint()
      val (label, children) = renderLabelAndChildren(collectionName, _.getLabel, checkChildren, collectionLength)(timeout)
      val classRenderer: ClassRenderer = NodeRendererSettings.getInstance().getClassRenderer
      val typeName = classRenderer.renderTypeName(collectionClass)
      val expectedLabel = s"$collectionName = {$typeName@$UNIQUE_ID}$afterTypeLabel"

      assertEquals("node label value doesn't match", expectedLabel, label)

      if (checkChildren) {
        val intType = classRenderer.renderTypeName("java.lang.Integer")
        val intLabel = s"{$intType@$UNIQUE_ID}"
        var testIndex = 0
        children.foreach { childLabel =>
          val expectedChildLabel = s"$testIndex = $intLabel${testIndex + 1}"

          try
            assertEquals(expectedChildLabel, childLabel)
          catch {
            case err: AssertionError =>
              val childrenDebugText = children.zipWithIndex
                .map { case (child, idx) => s"$idx: $child"}
                .mkString("\n")
              System.err.println(s"all children nodes labels:\n$childrenDebugText")
              throw err
          }
          testIndex += 1
        }
      }
    }
  }

  protected def testCollectionRenderer(collectionName: String,
                                       collectionClass: String,
                                       afterTypeLabel: String,
                                       expectedChildrenLabels: Seq[String])
                                      (implicit timeout: Duration): Unit = {

    import org.junit.Assert._
    runDebugger() {
      waitForBreakpoint()
      val (label, childrenLabels) =
        renderLabelAndChildren(collectionName, _.getLabel, renderChildren = expectedChildrenLabels.nonEmpty, expectedChildrenLabels.size)

      val classRenderer: ClassRenderer = NodeRendererSettings.getInstance().getClassRenderer
      val typeName = classRenderer.renderTypeName(collectionClass)
      val expectedLabel = s"$collectionName = {$typeName@$UNIQUE_ID}$afterTypeLabel"

      assertEquals(expectedLabel, label)
      assertEquals(expectedChildrenLabels, childrenLabels)
    }
  }

  protected def testScalaCollectionRenderer(collectionName: String, collectionLength: Int, collectionClass: String): Unit = {
    val shortClassName = ScalaCollectionRenderer.extractNonQualifiedName(collectionClass)
    val afterTypeLabel = s"$shortClassName size = $collectionLength"
    testCollectionRenderer(collectionName, collectionClass, afterTypeLabel, collectionLength, checkChildren = true)
  }

  protected def testScalaCollectionRenderer(collectionName: String,
                                            collectionClass: String,
                                            expectedChildrenLabels: Seq[String])
                                           (implicit timeout: Duration): Unit = {
    val collectionLength = expectedChildrenLabels.size
    val shortClassName = ScalaCollectionRenderer.extractNonQualifiedName(collectionClass)
    val afterTypeLabel = s"$shortClassName size = $collectionLength"
    testCollectionRenderer(collectionName, collectionClass, afterTypeLabel, expectedChildrenLabels)(timeout)
  }

  protected def testLazyCollectionRendering(collectionName: String, collectionClass: String, afterTypeLabel: String)
                                           (implicit timeout: Duration = DefaultTimeout): Unit =
    testCollectionRenderer(collectionName, collectionClass, afterTypeLabel, -1, checkChildren = false)(timeout)

  addFileWithBreakpoints("ShortList.scala",
    s"""
       |object ShortList {
       |  def main(args: Array[String]): Unit = {
       |    val lst = List(1, 2, 3, 4, 5, 6)
       |    val a = 1$bp
       |  }
       |}
      """.replace("\r", "").stripMargin.trim
  )
  def testShortList(): Unit = {
    testScalaCollectionRenderer("lst", 6, "scala.collection.immutable.$colon$colon")
  }


  addFileWithBreakpoints("Stack.scala",
    s"""
       |object Stack {
       |  def main(args: Array[String]): Unit = {
       |    import scala.collection.mutable
       |    val stack = mutable.Stack(1,2,3,4,5,6,7,8)
       |    val b = 45$bp
       |  }
       |}
      """.stripMargin.replace("\r","").trim
  )
  def testStack(): Unit = {
    testScalaCollectionRenderer("stack", 8, "scala.collection.mutable.Stack")
  }

  addFileWithBreakpoints("ListBuffer.scala",
    s"""
       |object MutableList {
       |  def main(args: Array[String]): Unit = {
       |    val mutableList = scala.collection.mutable.ListBuffer(1,2,3,4,5)
       |    val a = 1$bp
       |  }
       |}
    """.stripMargin.replace("\r", "").trim
  )
  def testMutableList(): Unit = {
    testScalaCollectionRenderer("mutableList", 5, "scala.collection.mutable.ListBuffer")
  }

  addFileWithBreakpoints("Queue.scala",
    s"""
       |object Queue {
       |  def main(args: Array[String]): Unit = {
       |    val queue = scala.collection.immutable.Queue(1,2,3,4)
       |    val a = 1$bp
       |  }
       |}
      """.stripMargin.replace("\r", "").trim
  )
  def testQueue(): Unit = {
    testScalaCollectionRenderer("queue", 4, "scala.collection.immutable.Queue")
  }

  addFileWithBreakpoints("QueueWithLongToStringChildren.scala",
    s"""object QueueWithLongToStringChildren {
       |  def main(args: Array[String]): Unit = {
       |    val queue = scala.collection.immutable.Queue(
       |      new LongToString(0),
       |      new LongToString(1),
       |      new LongToString(2),
       |      new LongToString(3),
       |      new LongToString(4)
       |    )
       |    val a = 1$bp
       |  }
       |}
       |
       |class LongToString(idx: Int) {
       |  override def toString: String = {
       |    Thread.sleep(1000) // ######### EMULATE LONG TO STRING EVALUATION #########
       |    s"To string result $$idx!"
       |  }
       |}""".stripMargin.replace("\r", "").trim
  )
  def testQueueWithLongToStringChildren(): Unit = {
    val expectedChildrenLabels = Seq(
      s"""0 = {LongToString@$UNIQUE_ID}To string result 0!""",
      s"""1 = {LongToString@$UNIQUE_ID}To string result 1!""",
      s"""2 = {LongToString@$UNIQUE_ID}To string result 2!""",
      s"""3 = {LongToString@$UNIQUE_ID}To string result 3!""",
      s"""4 = {LongToString@$UNIQUE_ID}To string result 4!""",
    )
    testScalaCollectionRenderer("queue", "scala.collection.immutable.Queue", expectedChildrenLabels)(30.seconds)
  }

  addFileWithBreakpoints("LongList.scala",
    s"""
       |object LongList {
       |  def main(args: Array[String]): Unit = {
       |    val longList = (1 to 50).toList
       |    val a = 1$bp
       |  }
       |}
      """.stripMargin.replace("\r", "").trim
  )
  def testLongList(): Unit = {
    testScalaCollectionRenderer("longList", 50, "scala.collection.immutable.$colon$colon")
  }
}
