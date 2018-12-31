package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[PerfCycleTests]))
class ExistentialsTest extends TypeInferenceTestBase {
  def testSCL4943(): Unit = doTest {
    """
      |object SCL4943 {
      |  class Bar {
      |    class Baz
      |  }
      |  class Foo {
      |    def foo = {
      |      val bar = new Bar
      |      object Temp {
      |        def foo(x: (b#Baz forSome { type b >: Bar <: Bar })): Int = 1
      |        def foo(s: String): String = s
      |      }
      |      /*start*/Temp.foo(new bar.Baz())/*end*/
      |      new bar.Baz()
      |    }
      |  }
      |}
      |//Int
    """.stripMargin.trim
  }
}
