package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlitghtingTestBase
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 07/06/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class JavaAnnotationTest extends JavaHighlitghtingTestBase {

  def testSCL10385() = {
    val scala =
      """
        |case class IdeBugFail @Inject() @Singleton()(var1: String)
      """.stripMargin

    val java =
      """
        |public @interface Singleton {
        |}
        |public @interface Inject {
        |    boolean optional() default false;
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

}
