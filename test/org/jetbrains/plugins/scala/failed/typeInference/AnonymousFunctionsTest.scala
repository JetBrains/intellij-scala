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

  def testSCL8621(): Unit = doTest {
    """
      |trait A[T] {
      |  def foo(x : T => T)
      |}
      |
      |trait B {
      |  def f(p : A[_]) {
      |    p.foo(/*start*/x => x/*end*/)
      |  }
      |}
      |//(_$1) => _$1
    """.stripMargin.trim
  }

  def testSCL9701(): Unit = doTest {
    """
      |def f(arg: (String*) => Unit) = {}
      |def ff(arg: Seq[String]) = {}
      |
      |f(s => ff(/*start*/s/*end*/))
      |//Seq[String]
    """.stripMargin.trim
  }

}
