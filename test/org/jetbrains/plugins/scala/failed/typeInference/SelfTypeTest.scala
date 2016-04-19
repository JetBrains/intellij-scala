package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/23/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class SelfTypeTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL5571(): Unit = doTest()

  def testSCL5947(): Unit = doTest()

  def testSCL8661(): Unit = doTest()

  def testSCL10173a(): Unit = doTest(
    s"""
       |trait T
       |class Root
       |class A extends Root with T
       |class B extends A {
       |  def foo(node: Root with T): Unit = {}
       |}
       |
       |object Example extends App {
       |
       |  def bug1(b: B): Unit = {
       |    val a: A = new A()
       |    b.foo(${START}a$END)
       |  }
       |}
       |//b.type
      """.stripMargin)

  def testSCL10173b(): Unit = doTest(
    s"""
       |trait T
       |class Root
       |class A extends Root with T
       |class B extends A {
       |  def foo(node: Root with T): Unit = {}
       |}
       |
       |object Example extends App {
       |  def bug2(b: B): Unit = {
       |    val b2: B = new B()
       |    b.foo(${START}b2$END)
       |  }
       |}
       |//b.type
      """.stripMargin)

  def testSCL8648(): Unit = {
    doTest(
      s"""
        |trait C {
        |    type T
        |  }
        |
        |  trait A[S]
        |
        |  trait B {
        |    def bar(x : A[C { type T }]) : A[C] = ${START}x$END
        |  }
        |//A[C]
      """.stripMargin)
  }

  def testSCL9738(): Unit = {
    doTest(
      s"""
         |sealed trait FeedbackReason
         |case object CostReason extends FeedbackReason
         |case object BugsReason extends FeedbackReason
         |case object OtherReason extends FeedbackReason
         |
         |object FeedbackTypes {
         |  def asMap(): Map[FeedbackReason, String] = ${START}{
         |    val reasons = Map(
         |      CostReason -> "It's too expensive",
         |      BugsReason -> "It's buggy"
         |    )
         |    reasons ++ Map(OtherReason -> "Some other reason")
         |  }$END
         |}
         |//Map[FeedbackReason, String]
      """.stripMargin)
  }

  def testSCL3959(): Unit = {
    doTest(
      s"""
         |class Z[T]
         |class B[T] {
         |  def foo(x: T) = x
         |}
         |
         |def foo1[T]: Z[T] = new Z[T]
         |def goo1[T](x: Z[T]): B[T] = new B[T]
         |goo1(foo1) foo ${START}1$END
         |//Nothing
      """.stripMargin)
  }
}
