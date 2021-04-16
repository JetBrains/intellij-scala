package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingTestBase

/**
  * @author Alefas
  * @since 23/03/16
  */
class JavaHighlightingTest extends JavaHighlightingTestBase() {
  override protected def shouldPass: Boolean = false

  def testSCL8982(): Unit = {
    val scala =
      """
        |object Foo {
        |  class Bar {
        |
        |  }
        |}
      """.stripMargin
    val java =
      """
        |public class Main {
        |    public static void main(String[] args) {
        |        new Foo$Bar();
        |    }
        |}
        |
      """.stripMargin
    assertNothing(errorsFromJavaCode(scala, java, "Main"))
  }

  def testSCL9663B(): Unit = {
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
    assertNothing(errorsFromJavaCode(scala, java, "Test"))
  }

  def testSCL6409(): Unit = {
    val java =
      """
        |public class JavaDummy<T> {
        |    public void method(JavaDummy<? super JavaDummy<?>> arg) {}
        |}""".stripMargin

    val scala =
      """
        |class Inheritor extends JavaDummy[Int] {
        |  override def method(arg: JavaDummy[_ <: JavaDummy[_]]): Unit = super.method(arg)
        |}""".stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL6114(): Unit = {
    val scala =
      """
        |package foo;
        |
        |package bar {
        |  class Test
        |}
      """.stripMargin

    val java =
      """
        |package foo;
        |
        |class A {
        |    public bar.Test something;  // Test is red - cannot resolve symbol Test.
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "A"))
  }

  def testSCL8639(): Unit = {
    val java =
      """
        |public abstract class Java<S> {
        |    public abstract class JavaInner {
        |        abstract void foo(S arg);
        |    }
        |}
        |
      """.stripMargin

    val scala =
      """
        |class Scala extends Java[String]{
        |  val s = new JavaInner {
        |    override def foo(arg: String): Unit = {}
        |  }
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL8666(): Unit = {
    val java =
      """
        |import scala.Function0;
        |import scala.Function1;
        |
        |import java.util.concurrent.Callable;
        |import java.util.function.Function;
        |
        |public class Lambdas {
        |
        |    public static <A> A doIt(Callable<A> f) {
        |        System.out.println("callable");
        |        try {
        |            return f.call();
        |        } catch (final Exception ex) {
        |            throw new RuntimeException(ex);
        |        }
        |    }
        |
        |    public static <A> A doIt(final Function0<A> f) {
        |        System.out.println("java_func");
        |        try {
        |            return f.apply();
        |        } catch (final Exception ex) {
        |            throw new RuntimeException(ex);
        |        }
        |    }
        |
        |    public static void doIt(Runnable f) {
        |        System.out.println("runnable");
        |        try {
        |            f.run();
        |        } catch (final Exception ex) {
        |            throw new RuntimeException(ex);
        |        }
        |    }
        |
        |    public static void main(final String... args) {
        |        final Lambdas l = new Lambdas();
        |        Lambdas.doIt(() -> {
        |            int x = 3;
        |        });
        |        Lambdas.doIt(() -> 24);
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode("", java, "Lambdas"))
  }

  def testSCL10531(): Unit = {
    val java =
      """
        |public interface Id {
        |    static scala.Option<Id> unapply(Id id) {
        |        // Can't define this in Scala because the static forwarder in companion class
        |        // conflicts with the interface trait.  Should be fixed in 2.12.
        |        // https://github.com/scala/scala-dev/issues/59
        |        if (id == NoId.instance()) {
        |            return scala.Option.empty();
        |        }
        |        return scala.Option.apply(id);
        |    }
        |}
        |
        |class NoId {
        |    public static Id instance() {
        |        return null;
        |    }
        |}
      """.stripMargin

    val scala =
      """
        |class mc {
        |  NoId.instance() match {
        |    case Id(id) =>
        |      true
        |  }
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }
}
