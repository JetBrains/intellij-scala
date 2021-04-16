package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.failed.annotator.OverloadingTestBase

class OverloadingTest extends OverloadingTestBase {
  def testSCL11684(): Unit = assertNothing(
    collectMessages(
      """
        |package pack1 {
        |
        |  trait Enclosure {
        |
        |    class A[T] {}
        |
        |    private [pack1] class B[T] extends A[T] {}
        |
        |    class C[T] extends B[T] {}
        |  }
        |}
        |
        |object Tester extends pack1.Enclosure {
        |
        |  trait Tweak[-S]
        |
        |  case class Sort[I](f: I => Int) extends Tweak[A[I]]
        |
        |  val x: Tweak[C[String]] = Sort[String](_.size)
        |}
      """.stripMargin)
  )
}
