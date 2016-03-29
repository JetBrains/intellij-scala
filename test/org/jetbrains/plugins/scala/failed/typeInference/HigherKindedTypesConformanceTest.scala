package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 28.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class HigherKindedTypesConformanceTest extends TypeConformanceTestBase {

  def testSCL9713(): Unit = doTest(
    """
      |import scala.language.higherKinds
      |
      |type Foo[_]
      |type Bar[_]
      |type S
      |def foo(): Foo[S] with Bar[S]
      |
      |val x: Foo[S] = foo()
      |//True
    """.stripMargin
  )

  def testSCL7319(): Unit = doTest {
    """
      |object SCL7319 {
      |
      |  trait XIndexedStateT[F[+_], -S1, +S2, +A] {
      |    def lift[M[+_]]: XIndexedStateT[({type λ[+α]=M[F[α]]})#λ, S1, S2, A] = ???
      |  }
      |
      |  type XStateT[F[+_], S, +A] = XIndexedStateT[F, S, S, A]
      |
      |  type XId[+X] = X
      |
      |
      |  implicit def example[S, A](s: XStateT[XId, S, A]): XStateT[Option, S, A] = {
      |    object Temp {
      |      def foo(s: XStateT[Option, S, A]): Int = 1
      |      def foo(s: Boolean): Boolean = false
      |    }
      |    /*start*/Temp.foo(s.lift[Option])/*end*/
      |    s.lift[Option]
      |  }
      |}
      |//Int
    """.stripMargin.trim
  }

}
