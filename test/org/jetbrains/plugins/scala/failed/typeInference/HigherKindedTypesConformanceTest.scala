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

}
