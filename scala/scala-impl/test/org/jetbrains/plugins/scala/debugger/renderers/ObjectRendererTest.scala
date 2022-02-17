package org.jetbrains.plugins.scala
package debugger
package renderers

import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.openapi.util.Disposer
import com.sun.jdi.ObjectReference
import org.jetbrains.plugins.scala.util.runners.TestScalaVersion._
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions}
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise, TimeoutException}
import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(Scala_2_11, Scala_2_12, Scala_2_13, Scala_3_0, Scala_3_1))
@Category(Array(classOf[DebuggerTests]))
class ObjectRendererTest extends RendererTestBase {

  addFileWithBreakpoints("test/ObjectRenderer.scala",
    s"""package test
       |
       |object ObjectRenderer {
       |
       |  private[this] val privateThisVal: Int = 1
       |  private val privateVal: Int = 2
       |  private[test] val packagePrivateVal: Int = 3
       |  val publicVal: Int = 4
       |
       |  private[this] var privateThisVar: Int = 5
       |  private var privateVar: Int = 6
       |  private[test] var packagePrivateVar: Int = 7
       |  var publicVar: Int = 8
       |
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}
       |""".stripMargin)

  def testObjectRenderer(): Unit = {
    checkValues(
      "privateThisVal" -> "1",
      "privateVal" -> "2",
      "packagePrivateVal" -> "3",
      "publicVal" -> "4",
      "privateThisVar" -> "5",
      "privateVar" -> "6",
      "packagePrivateVar" -> "7",
      "publicVar" -> "8"
    )
  }

  private def checkValues(fields: (String, String)*)(implicit timeout: Duration = DefaultTimeout): Unit = {
    runDebugger("test.ObjectRenderer") {
      waitForBreakpoint()
      val context = evaluationContext()

      val frameTree = new ThreadsDebuggerTree(getProject)
      Disposer.register(getTestRootDisposable, frameTree)

      val promise = Promise[Map[String, String]]()

      inSuspendContextAction(timeout, "Obtaining object fields took too long") {
        val module = context.computeThisObject().asInstanceOf[ObjectReference]
        val moduleFields = module.referenceType().fields().asScala.filterNot(_.name().contains("MODULE$"))
        val nodeFactory = frameTree.getNodeFactory

        val values = moduleFields.map { field =>
          val fieldDescriptor = nodeFactory.getFieldDescriptor(null, module, field)
          (field.name(), fieldDescriptor.calcValue(context).toString)
        }.toMap

        promise.success(values)
      }

      val values =
        try Await.result(promise.future, timeout)
        catch {
          case e: TimeoutException =>
            val message = s"Obtaining object fields took too long: ${e.getMessage}"
            val error = new AssertionError(message, e)
            error.setStackTrace(e.getStackTrace)
            throw error
        }

      fields.foreach { case (field, value) =>
        values.get(field).foreach { v =>
          assertEquals(v, value)
        }
      }
    }
  }
}
