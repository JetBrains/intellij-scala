package org.jetbrains.plugins.scala.failed.typeInference

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.javaHighlighting.JavaHighltightingTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 23/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class JavaHighlightingTest extends JavaHighltightingTestBase {
  def testSCL9663B() = {
    val scala =
      """
        |class Foo(val cell: String) extends AnyVal {
        |  def foo(x: Int) = 123
        |}
      """.stripMargin
    val java =
      """
        |public class Test {
        |    public static void main(String[] args) {
        |        Foo$ foo = Foo$.MODULE$;
        |
        |        foo.foo$extension("text", 1);
        |    }
        |}
      """.stripMargin
    assertNoErrors(messagesFromJavaCode(scala, java, "Test"))
  }

  def testSCL7525() = {
    val scala =
      """
        |package SCL7525
        |object Test {
        |  new Foo(new Foo.ArgsBar)
        |}
      """.stripMargin

    val java =
      """
        |package SCL7525;
        |public class Foo {
        |    public Foo(Args a) { }
        |    public static class Args<T extends Args<T>> { }
        |    public static class ArgsBar extends Args<ArgsBar> { }
        |}
      """.stripMargin

    assertNoErrors(messagesFromScalaCode(scala, java))
  }
}
