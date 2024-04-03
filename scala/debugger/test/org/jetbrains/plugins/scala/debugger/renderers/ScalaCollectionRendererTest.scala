package org.jetbrains.plugins.scala
package debugger
package renderers

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render._

class ScalaCollectionRendererTest_2_11 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class ScalaCollectionRendererTest_2_12 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  addSourceFile("Lazy.scala",
    s"""
       |object Lazy {
       |  def main(args: Array[String]): Unit = {
       |    val stream = Stream.from(1)
       |    val a = 1 $breakpoint
       |  }
       |}
      """.stripMargin
  )

  def testLazy(): Unit = {
    rendererTest() { implicit ctx =>
      testCollectionRenderer("stream", "scala.collection.immutable.Stream$Cons", "size = ?", renderChildren = false)
    }
  }
}

class ScalaCollectionRendererTest_2_13 extends ScalaCollectionRendererTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  addSourceFile("Lazy.scala",
    s"""
       |object Lazy {
       |  def main(args: Array[String]): Unit = {
       |    val list = LazyList.from(1)
       |    val stream = Stream.from(1)
       |    val a = 1 $breakpoint
       |    val b = 2
       |  }
       |}
      """.stripMargin
  )

  def testLazy(): Unit = {
    rendererTest() { implicit ctx =>
      testCollectionRenderer("list", "scala.collection.immutable.LazyList", "size = ?", renderChildren = false)
      testCollectionRenderer("stream", "scala.collection.immutable.Stream$Cons", "size = ?", renderChildren = false)
    }
  }
}

class ScalaCollectionRendererTest_3 extends ScalaCollectionRendererTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class ScalaCollectionRendererTest_3_RC extends ScalaCollectionRendererTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class ScalaCollectionRendererTestBase extends RendererTestBase {
  private val UNIQUE_ID = "uniqueID"

  protected def testCollectionRenderer(collectionName: String,
                                       collectionClass: String,
                                       afterTypeLabel: String,
                                       renderChildren: Boolean = true)(implicit context: SuspendContextImpl): Unit = {
    val (label, children) = renderLabelAndChildren(collectionName, renderChildren)
    val classRenderer: ClassRenderer = NodeRendererSettings.getInstance().getClassRenderer
    val typeName = classRenderer.renderTypeName(collectionClass)
    val expectedLabel = s"$collectionName = {$typeName@$UNIQUE_ID}$afterTypeLabel"

    assertEquals(expectedLabel, label)

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
            .map { case (child, idx) => s"$idx: $child" }
            .mkString("\n")
          System.err.println(s"all children nodes labels:\n$childrenDebugText")
          throw err
      }
      testIndex += 1
    }
  }

  protected def testScalaCollectionRenderer(collectionName: String, collectionLength: Int, collectionClass: String)(implicit context: SuspendContextImpl): Unit = {
    val afterTypeLabel = s"size = $collectionLength"
    testCollectionRenderer(collectionName, collectionClass, afterTypeLabel)
  }

  addSourceFile("ShortList.scala",
    s"""
       |object ShortList {
       |  def main(args: Array[String]): Unit = {
       |    val lst = List(1, 2, 3, 4, 5, 6)
       |    val a = 1 $breakpoint
       |  }
       |}
      """.stripMargin
  )

  def testShortList(): Unit = {
    rendererTest() { implicit ctx =>
      testScalaCollectionRenderer("lst", 6, "scala.collection.immutable.$colon$colon")
    }
  }

  addSourceFile("Stack.scala",
    s"""
       |object Stack {
       |  def main(args: Array[String]): Unit = {
       |    import scala.collection.mutable
       |    val stack = mutable.Stack(1, 2, 3, 4, 5, 6, 7, 8)
       |    val b = 45 $breakpoint
       |  }
       |}
      """.stripMargin
  )

  def testStack(): Unit = {
    rendererTest() { implicit ctx =>
      testScalaCollectionRenderer("stack", 8, "scala.collection.mutable.Stack")
    }
  }

  addSourceFile("HashMap.scala",
    s"""
       |object HashMap {
       |  def main(args: Array[String]): Unit = {
       |    import scala.collection.mutable
       |    val hashMap = mutable.HashMap(1 -> "one", 2 -> "two", 3 -> "three")
       |    val b = 45 $breakpoint
       |  }
       |}
      """.stripMargin
  )

  def testHashMap(): Unit = {
    val collectionName = "hashMap"
    rendererTest() { implicit ctx =>
      val (label, childrenLabels) = renderLabelAndChildren(collectionName, renderChildren = true)

      val classRenderer: ClassRenderer = NodeRendererSettings.getInstance().getClassRenderer
      val typeName = classRenderer.renderTypeName("scala.collection.mutable.HashMap")
      val expectedLabel = s"$collectionName = {$typeName@$UNIQUE_ID}size = 3"

      assertEquals(expectedLabel, label)
      Seq("{Tuple2@uniqueID}(1,one)", "{Tuple2@uniqueID}(2,two)", "{Tuple2@uniqueID}(3,three)").foreach { label =>
        org.junit.Assert.assertTrue(childrenLabels.exists(_.contains(label)))
      }
    }
  }

  addSourceFile("ListBuffer.scala",
    s"""
       |object MutableList {
       |  def main(args: Array[String]): Unit = {
       |    val mutableList = scala.collection.mutable.ListBuffer(1,2,3,4,5)
       |    val a = 1 $breakpoint
       |  }
       |}
    """.stripMargin
  )

  def testMutableList(): Unit = {
    rendererTest() { implicit ctx =>
      testScalaCollectionRenderer("mutableList", 5, "scala.collection.mutable.ListBuffer")
    }
  }

  addSourceFile("Queue.scala",
    s"""
       |object Queue {
       |  def main(args: Array[String]): Unit = {
       |    val queue = scala.collection.immutable.Queue(1,2,3,4)
       |    val a = 1 $breakpoint
       |  }
       |}
      """.stripMargin
  )

  def testQueue(): Unit = {
    rendererTest() { implicit ctx =>
      testScalaCollectionRenderer("queue", 4, "scala.collection.immutable.Queue")
    }
  }

  addSourceFile("QueueWithLongToStringChildren.scala",
    s"""object QueueWithLongToStringChildren {
       |  def main(args: Array[String]): Unit = {
       |    val queue = scala.collection.immutable.Queue(
       |      new LongToString(0),
       |      new LongToString(1),
       |      new LongToString(2),
       |      new LongToString(3),
       |      new LongToString(4)
       |    )
       |    val a = 1 $breakpoint
       |  }
       |}
       |
       |class LongToString(idx: Int) {
       |  override def toString: String = {
       |    Thread.sleep(200L) // ######### EMULATE LONG TO STRING EVALUATION #########
       |    s"To string result $$idx!"
       |  }
       |}""".stripMargin
  )

  def testQueueWithLongToStringChildren(): Unit = {
    val expectedChildrenLabels = Seq(
      s"""0 = {LongToString@$UNIQUE_ID}To string result 0!""",
      s"""1 = {LongToString@$UNIQUE_ID}To string result 1!""",
      s"""2 = {LongToString@$UNIQUE_ID}To string result 2!""",
      s"""3 = {LongToString@$UNIQUE_ID}To string result 3!""",
      s"""4 = {LongToString@$UNIQUE_ID}To string result 4!""",
    )
    val collectionLength = expectedChildrenLabels.size
    val collectionName = "queue"
    val afterTypeLabel = s"size = $collectionLength"
    rendererTest() { implicit ctx =>
      val (label, childrenLabels) =
        renderLabelAndChildren(collectionName, renderChildren = true)

      val classRenderer: ClassRenderer = NodeRendererSettings.getInstance().getClassRenderer
      val typeName = classRenderer.renderTypeName("scala.collection.immutable.Queue")
      val expectedLabel = s"$collectionName = {$typeName@$UNIQUE_ID}$afterTypeLabel"

      assertEquals(expectedLabel, label)
      assertEquals(expectedChildrenLabels, childrenLabels)
    }
  }

  addSourceFile("LongList.scala",
    s"""
       |object LongList {
       |  def main(args: Array[String]): Unit = {
       |    val longList = (1 to 50).toList
       |    val a = 1 $breakpoint
       |  }
       |}
      """.stripMargin
  )

  def testLongList(): Unit = {
    rendererTest() { implicit ctx =>
      testScalaCollectionRenderer("longList", 50, "scala.collection.immutable.$colon$colon")
    }
  }
}
