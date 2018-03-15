package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingTestBase
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 07/06/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class JavaAnnotationTest extends JavaHighlightingTestBase() {
  override protected def shouldPass: Boolean = false
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

  def testSCL11283() = {
    val scala =
      """
      """.stripMargin

    val java =
      """
        |public class Whatever {
        |    public <K, V> Map<K, V> convert(java.util.Map<K, V> m) {
        |        return JavaConverters$.MODULE$.mapAsScalaMapConverter(m).asScala().toMap(
        |                scala.Predef$.MODULE$.<scala.Tuple2<K, V>>conforms()
        |        );
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "Whatever"))
  }

}
