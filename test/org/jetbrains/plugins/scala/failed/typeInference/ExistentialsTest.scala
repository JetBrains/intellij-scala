package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 22/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class ExistentialsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL10037(): Unit = doTest()
  
  def testSCL9474() = doTest()

  def testSCL7895() = doTest(
    """
      |object SCL7895 {
      |  import scala.language.existentials
      |
      |  trait F[A] {def f}
      |  def t: Iterable[(F[A],F[A]) forSome {type A}] = ???
      |
      |  def fail = t.foreach{case (f1,f2) =>
      |   def f3 = f1
      |  /*start*/f3/*end*/.f} // doesn't recognize f1 type here
      |}
      |//SCL7895.F[_]
    """.stripMargin.trim
  )

  def testSCL8610(): Unit = doTest {
    """
      |object SCL8610 {
      |trait A[T] {
      |  def foo(x : T => T)
      |}
      |
      |trait B {
      |  def bar(x : A[_]) {
      |    x.foo(/*start*/y => y/*end*/) // here
      |  }
      |}
      |}
      |//(_$1) => _$1
    """.stripMargin.trim
  }
}
