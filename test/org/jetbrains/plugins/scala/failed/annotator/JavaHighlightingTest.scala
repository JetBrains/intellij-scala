package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlitghtingTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 23/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class JavaHighlightingTest extends JavaHighlitghtingTestBase {

  def testSCL8982() = {
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
    assertNothing(errorsFromJavaCode(scala, java, "Test"))
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

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL9029() = {
    val scala =
      """
        |package scl9029
        |import java.lang.invoke.{MethodHandles, MethodType}
        |
        |class SCL9029 {
        |  def a: Int = 5
        |
        |  def b = {
        |    val mh = MethodHandles.publicLookup().findVirtual(
        |      classOf[A], "a", MethodType.methodType(classOf[Int])
        |    )
        |    val z: Int = mh.invokeExact(this)
        |  }
        |}
      """.stripMargin

    val java =
      """
        |package scl9029;
        |public class Foo {
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }
  
  def testSCL6409() = {
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

  def testSCL7069() = {
    val scala =
      """
        |package z
        |import z.Test.U
        |
        |class R {
        |  val u: U[Any] = new U[Any]
        |
        |  Test.foo(u)
        |}
      """.stripMargin

    val java =
      """
        |package z;
        |public class Test {
        |    public static class U<T> {
        |
        |    }
        |
        |    public static int foo(U<? extends Object> u) {
        |        return 1;
        |    }
        |
        |    public static boolean foo(String s) {
        |        return false;
        |    }
        |}
        |
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL6114() = {
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

  def testSCL9871(): Unit = {
    val java =
      """
        |package foo.object;
        |
        |public class Related
        |{
        |    static String foo() { return "package scoped"; }
        |}
      """.stripMargin

    val scala =
      """
        |package foo.`object`
        |
        |import foo.`object`.Related // unneeded, but without this, Related is marked red below with message "cannot resolve symbol"
        |
        |object Escaping extends App {
        |  println(Related.foo) // foo is marked as red, with message "Symbol foo is inaccessible from this place"
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL10150(): Unit = {
    val java =
      """
        |package jPack1.object;
        |public class JClassFromObjPack { }
      """.stripMargin

    val scala =
      """
        |package sPack1
        |
        |class SClass {
        |  def x: JClassFromObjPack = new JClassFromObjPack
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
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

  def testSCL8759(): Unit = {
    val java =
      """
        |public class Foobar {
        |    public static void foo(Object something) {
        |    }
        |    public static <T extends Number> void foo(T something) {
        |    }
        |}
      """.stripMargin

    val scala =
      """
        |class ScClass {
        |  def method = {
        |    Foobar.foo("")
        |    Foobar.foo(java.lang.Integer.valueOf(1))
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

  def testClassParameterScala(): Unit = {
    val scala =
      """
        |class ScalaClass (var name: String, var surname: String)
        |
        |object Start {
        |  def main(args: Array[String]) {
        |    val scalaClassObj = new ScalaClass("Dom", "Sien")
        |    println(scalaClassObj.name)
        |    println(scalaClassObj.surname)
        |
        |    val javaClassObj = new JavaClass("Dom2", "Sien2", 31)
        |    println(javaClassObj.name)
        |    println(javaClassObj.surname)
        |    println(javaClassObj.getAge)
        |  }
        |}
      """.stripMargin

    val java =
      """
        |public class JavaClass extends ScalaClass {
        |  private int age;
        |
        |  public JavaClass(String name, String surname, int age) {
        |    super(name, surname);
        |    this.age = age;
        |  }
        |
        |  public int getAge() {
        |    return age;
        |  }
        |
        |  public void setAge(int age) {
        |    this.age = age;
        |  }
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testOptionApply(): Unit = {
    val java =
      """
        |import scala.Option;
        |
        |public abstract class OptionApply {
        |
        |    public OptionApply() {
        |        setAction(Option.apply("importVCardFile"));
        |    }
        |
        |    public abstract void setAction(Option<String> bar);
        |}
      """.stripMargin


    assertNothing(errorsFromJavaCode(scalaFileText = "", java, javaClassName = "OptionApply"))
  }
}
