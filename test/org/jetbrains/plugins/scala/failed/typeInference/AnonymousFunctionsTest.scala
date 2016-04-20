package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 25/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class AnonymousFunctionsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL8267(): Unit = doTest()

  def testSCL9432(): Unit = doTest {
    """
      |object SCL9432 {
      |  def f(int: Int): Option[Int] = if (int % 2 == 0) Some(int) else None
      |  def g(as: List[Int])(b: Int): Option[Int] = if (as contains b) None else f(b)
      |  /*start*/List(1) flatMap g(List(2, 4))/*end*/
      |}
      |//List[Int]
    """.stripMargin.trim
  }

  def testSCL4717(): Unit = doTest {
    """
      |object SCL4717 {
      |  def inc(x: Int) = x + 1
      |  def foo(f: Int => Unit) = f
      |
      |  val g: Int => Unit = inc _
      |  foo(/*start*/inc _/*end*/)
      |}
      |//(Int) => Unit
    """.stripMargin.trim
  }

  def testSCL7010(): Unit = doTest {
    """
      |object O {
      |    case class Z()      |
      |    def Z(i: Int) = 123      |
      |    val x: Int => Int = /*start*/Z/*end*/
      |  }
      |//(Int) => Unit
    """.stripMargin.trim
  }

}
