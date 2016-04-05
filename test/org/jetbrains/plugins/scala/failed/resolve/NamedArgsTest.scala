package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 05.04.16.
  */

@Category(Array(classOf[PerfCycleTests]))
class NamedArgsTest extends SimpleResolveTestBase {

  def testSCL9144(): Unit = {
    doResolveTest(
      """
        |class AB(val a: Int, val b: Int) {
        |  def withAB(x: Int) = ???
        |  def withAB(<tgt>a: Int = a, b: Int = b) = ???
        |  def withA(a: Int) = withAB(<src>a = a)
        |  def withB(b: Int) = withAB(b = b)
        |}
      """.stripMargin)
  }

}
