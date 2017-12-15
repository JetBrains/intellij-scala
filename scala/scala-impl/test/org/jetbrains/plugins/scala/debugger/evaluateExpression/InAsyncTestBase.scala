package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase
/**
  * @author Nikolay.Tropin
  */

//TODO blinking test
//class InAsyncTest extends InAsyncTestBase with ScalaVersion_2_11

abstract class InAsyncTestBase extends ScalaDebuggerTestCase {

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader("org.scala-lang.modules" %% "scala-async" % "0.9.5")
  )

  addFileWithBreakpoints("InAsync.scala",
   s"""
      |import scala.async.Async._
      |import scala.concurrent.Await
      |import scala.concurrent.duration.Duration
      |import scala.concurrent.ExecutionContext.Implicits.global
      |
      |object InAsync {
      |  def main(args: Array[String]) {
      |    val future = async {
      |      val q = "q"
      |      val f1 = async { false }
      |      val x = 1
      |      def inc(t: Int) = {
      |        def inner(): String = {
      |          val r = "r"
      |          r + q$bp //3rd
      |        }
      |        inner()
      |        t + x$bp //4th
      |      }
      |      val t = 0
      |      val f2 = async { 42 }
      |      "" $bp //1st
      |      if (await(f1))
      |        await(f2)
      |      else {
      |        val z = 1
      |        inc(t + z) $bp  //2nd
      |      }
      |    }
      |
      |    println(Await.result(future, Duration.Inf))
      |  }
      |}
    """.stripMargin)

  def testInAsync(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("q", "q")
      evalEquals("f1.value.get.get", "false")
      evalEquals("x", "1")
      evalEquals("t", "0")
      evalEquals("inc(1)", "2")
      evalEquals("f2.value.get.get", "42")
      atNextBreakpoint {
        evalEquals("q", "q")
        evalEquals("x", "1")
        evalEquals("t", "0")
        evalEquals("z", "1")
        evalEquals("inc(11)", "12")
      }
      atNextBreakpoint {
        evalEquals("q", "q")
        evalEquals("x", "1")
        evalEquals("t", "1")
        evalEquals("r", "r")
        evalEquals("inc(12)", "13")
        evalEquals("inner()", "rq")
      }
      atNextBreakpoint {
        evalEquals("q", "q")
        evalEquals("x", "1")
        evalEquals("t", "1")
        evalEquals("inc(13)", "14")
        evalEquals("inner()", "rq")
      }
    }
  }

}
