package org.jetbrains.plugins.scala
package javaHighlighting

import org.jetbrains.plugins.scala.annotator._


/**
  * Author: Svyatoslav Ilinskiy
  * Date: 7/8/15
  */
class JavaHighlightingTest extends JavaHighlightingTestBase {

  def testSignatures(): Unit = {
    val scala =
      """
        |class Moo extends Koo {
        |  override def foo = {
        |    new Blargle {
        |      def bar: Int = -1
        |    }
        |  }
        |}
      """.stripMargin

    val java =
      """
        |interface Blargle {
        |    public int bar();
        |}
        |public interface Foo {
        |    public Blargle foo();
        |}
        |
        |class Koo implements Foo {
        |    @Override
        |    public Blargle foo() {
        |        return null;
        |    }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL11645(): Unit = {
    val scala =
      """
        |trait Base {
        |  def foo(): String = "foo"
        |}
        |
        |class A1 extends Base
        |
        |trait A2 extends Base
      """.stripMargin

    val java =
      """
        |public class Test extends A1 implements A2 {
        |    {
        |        foo();
        |    }
        |}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scala, java, "Test"))
  }

  def testSCL12286(): Unit = {
    val java =
      """
        |import scala.Option;
        |import scala.Option$;
        |
        |import java.sql.Connection;
        |
        |public class Temp {
        |    static void test(Connection con, String s) {
        |        Option<Connection> conOpt = Option$.MODULE$.apply(con);
        |        Option<String> stringOpt = Option$.MODULE$.apply(s);
        |    }
        |}
        |
      """.stripMargin
    assertNothing(errorsFromJavaCode("", java, "Temp"))

  }

  def testProtected(): Unit = {
    val scala =
      """
        |class MeaningOfLifeSpec {
        |  val c = new UltimateQuestion {}
        |  def meaningOfLifeScala() {
        |    c.meaningOfLife()
        |  }
        |}
      """.stripMargin
    val java =
      """
        |public class UltimateQuestion {
        |    protected int meaningOfLife() {
        |        return 42; //Answer to the Ultimate Question of Life, the Universe, and Everything
        |    }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testTraitIsAbstract(): Unit = {
    val scalaCode = "trait MooSCL4289"
    val javaCode =
      """
        |public class TestSCL4289 {
        |    public static void main(String[] args) {
        |        new MooSCL4289();
        |    }
        |}
      """.stripMargin

    val defaultMessage = ScalaBundle.message("illegal.instantiation", "Trait", "MooSCL4289")
    assertMatches(errorsFromJavaCode(scalaCode, javaCode, "TestSCL4289")) {
      case Error("new MooSCL4289()", message) :: Nil if defaultMessage.contains(message) =>
    }
  }

  def testCallByNameParameterNoPrimitives(): Unit = {
    val scala =
      """
        |object MooSCL8823 {
        |  def ensure(f: => Unit): Unit = ???
        |}
      """.stripMargin
    val java =
      """
        |import scala.runtime.AbstractFunction0;
        |import scala.runtime.BoxedUnit;
        |
        |public class SCL8823 {
        |    public static void main( String[] args ) {
        |        MooSCL8823.ensure(new AbstractFunction0<BoxedUnit>() {
        |            public BoxedUnit apply() {
        |                System.out.println("foo");
        |                return BoxedUnit.UNIT;
        |            }
        |        });
        |    }
        |}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scala, java, "SCL8823"))
  }


  def testSCL7069(): Unit = {
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

  def testValueTypes(): Unit = {
    val scala =
      """
        |class Order(val limitPrice: Price, val qty: Quantity)
        |class Prices(val prices: java.util.List[Price])
        |
        |class Price(val doubleVal: Double) extends AnyVal
        |class Quantity(val doubleVal: Double) extends AnyVal
        |class Bar
        |class BarWrapper(val s: Bar) extends AnyVal
        |class BarWrappers(val bars: java.util.List[BarWrapper])
        |
      """.stripMargin
    val java =
      """
        |import java.util.ArrayList;
        |
        |public class JavaHighlightingValueTypes {
        |
        |    public static void main(String[] args) {
        |        Order o = new Order(19.0, 10);
        |        System.out.println("Hello World! " + o.limitPrice());
        |        Price p = new Price(10);
        |
        |        Prices pr = new Prices(new ArrayList<Price>());
        |        BarWrappers barWrappers = new BarWrappers(new ArrayList<Bar>());
        |
        |        doublePrice(new Price(10.0));
        |        doublePrice(42.0);
        |    }
        |
        |    public static void doublePrice(Price p) {
        |        System.out.println(p.doubleVal() * 2);
        |    }
        |
        |}
      """.stripMargin

    assertMatches(errorsFromJavaCode(scala, java, javaClassName = "JavaHighlightingValueTypes")) {
      case Error("(42.0)", message) :: Nil if message.contains("cannot be applied") =>
    }
  }

  def testSCL9029(): Unit = {
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
        |      classOf[SCL9029], "a", MethodType.methodType(classOf[Int])
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

  def testAccessBacktick(): Unit = {
    val scala =
      """
        |import scala.beans.BeanProperty
        |
        |case class TestAccessBacktick(@BeanProperty `type`:String)
      """.stripMargin

    val java =
      """
        |public class TestJavaAAA {
        |    public static void main(String[] args) {
        |        TestAccessBacktick t = new TestAccessBacktick("42");
        |        t.type();
        |        t.getType();
        |        t.get$u0060type$u0060();
        |    }
        |}
      """.stripMargin

    assertMatches(errorsFromJavaCode(scala, java, javaClassName = "TestJavaAAA")) {
      case Error("get$u0060type$u0060", message) :: Nil if message.contains("Cannot resolve method") =>
    }
  }

  def testMultipleThrowStatements(): Unit = {
    val scala = ""
    val java =
      """
        |import scala.concurrent.Await;
        |import scala.concurrent.Future;
        |import scala.concurrent.duration.Duration;
        |
        |import java.util.concurrent.TimeoutException;
        |
        |public class ThrowsJava {
        |    public void bar(Future<Integer> scalaFuture) {
        |        try {
        |            Await.ready(scalaFuture, Duration.Inf());
        |        } catch (InterruptedException e) {
        |            e.printStackTrace();
        |        } catch (TimeoutException e) {
        |            e.printStackTrace();
        |        }
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, javaClassName = "ThrowsJava"))
  }

  def testThrowsWithTypeArgError(): Unit = {
    val scalaThrows =
      """
        |abstract class Base {
        |  @throws[Exception]
        |  def test(): Unit
        |}
        |""".stripMargin

    val java =
      """
        |public class Impl extends Base {
        |    @Override
        |    public void test() throws Exception {}
        |}
        |""".stripMargin

    assertNothing(errorsFromJavaCode(scalaThrows, java, javaClassName = "Impl"))
  }

  def testThrowsWithTypeArgNoError(): Unit = {
    val scalaNoThrows =
      """
        |abstract class Base {
        |  def test(): Unit
        |}
        |""".stripMargin

    val java =
      """
        |public class Impl extends Base {
        |    @Override
        |    public void test() throws Exception {}
        |}
        |""".stripMargin

    assertMatches(errorsFromJavaCode(scalaNoThrows, java, javaClassName = "Impl")) {
      case Error("Exception", message) :: Nil if message.contains("overridden method does not throw") =>
    }
  }


  def testOverrideFinal(): Unit = {
    val scala = ""
    val java =
      """
        |import scala.Function1;
        |import scala.concurrent.ExecutionContext;
        |
        |public abstract class Future<T> implements scala.concurrent.Future<T> {
        |
        |    @Override
        |    public scala.concurrent.Future<T> withFilter(Function1<T, Object> pred, ExecutionContext executor) {
        |        return null;
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "Future"))
  }

  def testSCL5617Option(): Unit = {
    val scala = ""
    val java =
      """
        |import scala.Function1;
        |import scala.Option;
        |import scala.runtime.BoxedUnit;
        |import java.util.concurrent.atomic.AtomicReference;
        |import scala.runtime.AbstractFunction1;
        |
        |public class SCL5617 {
        |     public static void main(String[] args) {
        |        AtomicReference<Function1<Object, BoxedUnit>> f = new AtomicReference<Function1<Object, BoxedUnit>>(new AbstractFunction1<Object, BoxedUnit>() {
        |          public BoxedUnit apply(Object o) {
        |            Option<String> option = Option.empty();
        |            return BoxedUnit.UNIT;
        |          }
        |        });
        |
        |        Option<Function1<Object, BoxedUnit>> o = Option.apply(f.get());
        |    }
        |}
        |
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "SCL5617"))
  }

  def testCaseClassImplement(): Unit = {
    val scala = "case class CaseClass()"
    val java =
      """
        |public class CaseClassExtended extends CaseClass {
        |
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, javaClassName = "CaseClassExtended"))
  }

  def testOverrideDefaultWithStaticSCL8861(): Unit = {
    def scala =
      """
        |class TestKit2SCL8861 extends TestKitBase2SCL8861
        |
        |object TestKit2SCL8861 {
        |  def awaitCond(interval: String = ???): Boolean = {
        |    ???
        |  }
        |}
        |trait TestKitBase2SCL8861 {
        |  def awaitCond(interval: String = ???) = ???
        |}
      """.stripMargin

    val java =
      """
        |public class SCL8861 extends TestKit2SCL8861 {
        |
        |}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scala, java, "SCL8861"))
  }

  def testClassParameterJava(): Unit = {
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
        |    val javaClassObj = new JavaInheritor("Dom2", "Sien2", 31)
        |    println(javaClassObj.name)
        |    println(javaClassObj.surname)
        |    println(javaClassObj.getAge)
        |  }
        |}
      """.stripMargin

    val java =
      """
        |public class JavaInheritor extends ScalaClass {
        |  private int age;
        |
        |  public JavaInheritor(String name, String surname, int age) {
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

    assertNothing(errorsFromJavaCode(scala, java, "JavaInheritor"))
  }

  def testSCL3390ParamAccessorJava(): Unit = {
    val scalaCode =
      """
        |object ScalaClient {
        |  def main(args: Array[String]) {
        |    new Sub(1).x
        |  }
        |}
        |
        |class Super(val x: Int)
        |
        |class Sub(x: Int) extends Super(x)
      """.stripMargin
    val javaCode =
      """
        |public class JavaClientSCL3390 {
        |    public static void main(String[] args) {
        |        new Sub(1).x();
        |    }
        |}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scalaCode, javaCode, "JavaClientSCL3390"))
  }

  def testSCL3390ParamAccessorScala(): Unit = {
    val scalaCode =
      """
        |object ScalaClient {
        |  def main(args: Array[String]) {
        |    new Sub(1).x
        |  }
        |}
        |
        |class Super(val x: Int)
        |
        |class Sub(x: Int) extends Super(x)
      """.stripMargin
    val javaCode =
      """
        |public class JavaClientSCL3390 {
        |    public static void main(String[] args) {
        |        new Sub(1).x();
        |    }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scalaCode, javaCode))
  }


  def testSCL3498ExistentialTypesFromJava(): Unit = {
    val javaCode =
      """
        |public @interface Transactional {
        |    Class<? extends Throwable>[] noRollbackFor() default {};
        |}
      """.stripMargin
    val scalaCode =
      """
        |@Transactional(noRollbackFor = Array(classOf[RuntimeException])) // expected Array[Class[_ <: Throwable] found Array[Class[RuntimeException]]
        |class A
      """.stripMargin

    assertNothing(errorsFromScalaCode(scalaCode, javaCode))
  }

  def testResolvePublicJavaFieldSameNameAsMethod(): Unit = {
    val scalaCode =
      """
        |package SCL3679
        |
        |object ResolvePublicJavaFieldSameNameAsMethod {
        |  def main(args: Array[String]) {
        |    println("foo")
        |    new ResolvePublicJavaFieldSameNameAsMethodJavaClass().hasIsCompressed
        |  }
        |}
      """.stripMargin

    val javaCode =
      """
        |package SCL3679;
        |
        |public class ResolvePublicJavaFieldSameNameAsMethodJavaClass {
        |    public boolean hasIsCompressed;
        |    public boolean hasIsCompressed() {
        |        System.out.println("In the method!");
        |        return hasIsCompressed;
        |    }
        |
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scalaCode, javaCode))
  }

  def testRawTypeInheritance(): Unit = {
    val scalaCode =
      """
        |package inheritance
        |
        |class Af extends R
      """.stripMargin

    val javaCode =
      """
        |package inheritance;
        |
        |public class R implements Comparable {
        |    @Override
        |    public int compareTo(Object o) {
        |        return 0;
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scalaCode, javaCode))
  }

  def testGenericsPlainInnerClass(): Unit = {
    val scalaCode =
      """
        |trait FSM[S, D] {
        |  final class TransformHelper {}
        |  final def transform(): TransformHelper = ???
        |}
        |
        |
        |abstract class Base[S, D] extends FSM[S, D]
      """.stripMargin
    val javaCode =
      """
        |public class SCL8866A extends Base<String, String> {}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scalaCode, javaCode, javaClassName = "SCL8866A"))
  }

  def testOverrideScalaFromJavaUpperBound(): Unit = {
    val scalaCode =
      """
        |trait SCL5852WrapsSomething[T] {
        |  def wrap[A <: T](toWrap: A): A
        |}
      """.stripMargin
    val javaCode =
      """
        |public class SCL5852WrapsFoo implements SCL5852WrapsSomething<String> {
        |    @Override
        |    public <A extends String> A wrap(A toWrap) {
        |        return null;
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scalaCode, javaCode, javaClassName = "SCL5852WrapsFoo"))
  }

  def testGenericsParameterizedInnerClass(): Unit = {
    val scalaCode =
      """
        |abstract class FSM[S, D] {
        |  class TransformHelper[T]
        |  def transform(): TransformHelper[Int] = ???
        |}
        |
        |abstract class Base extends FSM[Int, String] {
        |  override def transform(): TransformHelper[Int] = ???
        |}
      """.stripMargin
    val javaCode =
      """
        |public class SCL8866B extends Base {
        |
        |}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scalaCode, javaCode, "SCL8866B"))
  }

  def testDefaultConstructorArguments(): Unit = {
    val scalaCode =
      """
        |class MooSCL7582(j: Int)(d: Int = j)
      """.stripMargin
    val javaCode =
      """
        |public class TestSCL7582 {
        |    public static void main(String[] args) {
        |        MooSCL7582 m =  new MooSCL7582(1, MooSCL7582.$lessinit$greater$default$2(1));
        |    }
        |}
      """.stripMargin

    assertMessages(errorsFromJavaCode(scalaCode, javaCode, "TestSCL7582"))(
      Error("$lessinit$greater$default$2","Cannot resolve method '$lessinit$greater$default$2' in 'MooSCL7582'")
    )
  }

  def testSpecializedFields(): Unit = {
    val scalaCode = "class SpecClass[@specialized(Int) T](val t: T, val s: String)"
    val javaCode =
      """
        |public class Pair extends SpecClass<Integer> {
        |    public Pair(SpecClass<Integer> i) {
        |        super(i.t, "");
        |    }
        |}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scalaCode, javaCode, "Pair"))
  }

  def testConstructorReturnTypeNull(): Unit = {
    val scalaCode =
      """
        |class Scala(val s: String) {
        |  def this(i: Integer) = this(i.toString)
        |}
      """.stripMargin
    val javaCode =
      """
        |import java.util.stream.Stream;
        |
        |public class SCL9412 {
        |    Stream<Scala> testScala() {
        |        return Stream.of(1).map(Scala::new);
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scalaCode, javaCode, "SCL9412"))
  }

  def testHigherKinded(): Unit = {
    val scalaCode =
      """
        |class BarSCL9661A[F, T[F]]() extends scala.AnyRef {
        |  def foo(t: T[F]): T[F] = t
        |}
      """.stripMargin
    val javaCode =
      """
        |import java.util.*;
        |
        |public class SCL9661A {
        |    public void create() {
        |        BarSCL9661A<String, List> bar = new BarSCL9661A<>();
        |        bar.foo(new ArrayList<Integer>());
        |    }
        |}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scalaCode, javaCode, "SCL9661A"))
  }

  def testClassParameterScala(): Unit = {
    val scala =
      """
        |class ScalaClassParamClass (var name: String, var surname: String)
        |
        |object Start {
        |  def main(args: Array[String]) {
        |    val scalaClassObj = new ScalaClassParamClass("Dom", "Sien")
        |    println(scalaClassObj.name)
        |    println(scalaClassObj.surname)
        |
        |    val javaClassObj = new JavaClassParamClass("Dom2", "Sien2", 31)
        |    println(javaClassObj.name)
        |    println(javaClassObj.surname)
        |    println(javaClassObj.getAge)
        |  }
        |}
      """.stripMargin

    val java =
      """
        |public class JavaClassParamClass extends ScalaClassParamClass {
        |  private int age;
        |
        |  public JavaClassParamClass(String name, String surname, int age) {
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

  def testSCL9619(): Unit = {
    val scala =
      """
        |@Annotaion(`lazy` = true)
        |class A {}
      """.stripMargin

    val java =
      """
        |public @interface Annotaion {
        |    public String db() default "";
        |
        |    public boolean lazy() default false;
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL9661(): Unit = {
    val scalaCode =
      """
        |object Moo extends scala.AnyRef {
        |  def builder[M]() : Builder[M] = ???
        |
        |  class Builder[+Mat] {
        |    def graph[S <: Shape](graph : Graph[S, _]) : S = { ??? }
        |  }
        |}
        |
        |class UniformFanOutShape[I, O] extends Shape
        |abstract class Shape
        |trait Graph[+S <: Shape, +M]
      """.stripMargin
    val javaCode =
      """
        |public class SCL9661 {
        |    public void create() {
        |        UniformFanOutShape<String, String> ass = Moo.builder().graph(null);
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scalaCode, javaCode, "SCL9661"))
  }

  def testSCL9871(): Unit = {
    val java =
      """
        |package foo.object;
        |
        |class Related {
        |    static String foo() { return "package scoped"; }
        |}
      """.stripMargin

    val scala =
      """
        |package foo.`object`
        |
        |object Escaping  {
        |  Related.foo
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL10232(): Unit = {
    val java =
      """
        |class Bug10232 {
        |    void foo(){
        |        Props.create(Actor.class, "xyz");
        |    }
        |
        |    class Actor extends UntypedActor {
        |        @Override
        |        public void onReceive(Object message) throws Exception {
        |
        |        }
        |    }
        |}
      """.stripMargin

    val scala =
      """
        |abstract class UntypedActor() extends scala.AnyRef{
        |  @scala.throws[scala.Exception](classOf[scala.Exception])
        |  def onReceive(message:scala.Any):scala.Unit
        |}
        |
        |object Props {
        |  def create(clazz : scala.Predef.Class[_], args : scala.AnyRef*) : Any = ???
        |  def create(creator : Any) : Any = ???
        |  def create(actorClass : scala.Predef.Class[_], creator : Any) : Any = ???
        |}
        |
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "Bug10232"))
  }

  def testSCL10236(): Unit = {
    val java =
      """
        |class Bug10236 {
        |
        |    void foo() {
        |        ActorSystem system = ActorSystem.create("aSystem", "something");
        |        Object random = system.actorOf(Props.create(RandomActor.class, 1));
        |    }
        |}
        |
        |class RandomActor {
        |    public RandomActor(Integer whatever) { }
        |}
      """.stripMargin

    val scala =
      """
        |class Props
        |object Props {
        |  @scala.annotation.varargs
        |  def create(clazz: scala.Predef.Class[_], args: scala.AnyRef*): Props = ???
        |}
        |
        |abstract class ActorSystem()
        |object ActorSystem extends scala.AnyRef {
        |  def create(name : scala.Predef.String, config : Any) : ActorSystem = ???
        |  def actorOf(props : Props) : Any = ???
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "Bug10236"))
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

  def testUpperBoundCompound(): Unit = {
    val java =
      """
        |package upperBoundCompound;
        |class BaseComponent {}
        |
        |interface ComponentManager {
        |    BaseComponent getComponent(String var1);
        |
        |    <T> T getComponent(Class<T> var1);
        |
        |    <T> T getComponent(Class<T> var1, T var2);
        |}
      """.stripMargin
    val scala =
      """
        |package upperBoundCompound
        |
        |class Foo
        |object Koo {
        |  def instance(p: ComponentManager) = {
        |
        |    p.getComponent(classOf[Foo])
        |  }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testJavaTypeParameterRaw(): Unit = {
    val scala =
      """
        |object Moo {
        |  def main(args: Array[String]): Unit = {
        |    val stub = new Stub[PsiElement]
        |    val first = stub.getChildren.get(0)
        |    val psi: PsiElement = first.getPsi
        |  }
        |}
      """.stripMargin
    val java =
      """
        |import java.util.List;
        |
        |public class Stub<T extends PsiElement> {
        |    public List<Stub> getChildren() {
        |        return null;
        |    }
        |    public T getPsi() {
        |        return null;
        |    }
        |}
        |
        |class PsiElement {}
        |
        |class BaseComponent {}
        |
        |interface ComponentManager {
        |    BaseComponent getComponent(String var1);
        |
        |    <T> T getComponent(Class<T> var1);
        |
        |    <T> T getComponent(Class<T> var1, T var2);
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL10478(): Unit = {
    val scala =
      """
        |class Moo(parameter: String) extends Parent[String]() {
        |  var typeText: String = parameter
        |}
      """.stripMargin
    val java =
      """
        |public abstract class Parent<P> {
        |    public final P parameter = null;
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testJavaGenericConstructor(): Unit = {
    val java =
      """
        |public class A {
        |  public <T> A( T t) {}
        |  public A(String s) {}
        |}
      """.stripMargin
    val scala =
      """
        |class U {
        |  new A(false)
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL7525(): Unit = {
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

  def testSCL10266(): Unit = {
    val java =
      """
        |interface Mixin<T extends Mixin<T>> {}
        |
        |class AcceptMixin {
        |    static void acceptMixin(Mixin mixin) {
        |        System.out.println(mixin);
        |    }
        |}
        |
        |class FooWithMixin implements Mixin<FooWithMixin> {}
      """.stripMargin

    val scala =
      """
        |object ScalaTest extends App {
        |  AcceptMixin.acceptMixin(new FooWithMixin());
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL13105(): Unit = {
    val java =
      """
        |public class JavaTestClass extends AbstractTestClass {
        |
        |    public static void main(String[] args){
        |        JavaTestClass jtc = new JavaTestClass();
        |        System.out.println(jtc.self());
        |    }
        |}
        |
      """.stripMargin

    val scala =
      """
        |trait TestTrait{
        |  implicit final val self = "self"
        |
        |}
        |
        |abstract class AbstractTestClass extends TestTrait
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "JavaTestClass"))
  }

  def testSCL11863(): Unit = {
    val scala =
      """
        |class Foo[T] {
        |  @scala.annotation.varargs
        |  def select(col: String, cols: String*): Foo[T] = this
        |
        |  def select(i: Int, j: Int) : Int = 0
        |}""".stripMargin

    val java =
      """
        |public class JavaTestClass {
        |
        |    public static void main(String[] args){
        |        Foo<String> foo = new Foo<>();
        |        foo.select("a", "b").select(1, 2);
        |    }
        |}""".stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "JavaTestClass"))
  }

  def testSCL11914(): Unit = {
    val scala =
      """
        |object Foo {
        |  def someFunction(default: String): Unit = {
        |  }
        |}
      """.stripMargin
    val java =
      """
        |public class Bar {
        |  void foo(){
        |    Foo.someFunction("cat");
        |  }
        |}
      """.stripMargin
    assertNothing(errorsFromJavaCode(scala, java, "Bar"))
  }

  def testSealedInheritors(): Unit = {
    val scalaFileText =
      s"""sealed trait Foo
         |sealed class Bar
       """.stripMargin

    val javaFileText =
      s"""public class Baz extends Bar implements Foo {
         |
         |  public static Foo createFoo() {
         |    return new Foo() {};
         |  }
         |}
       """.stripMargin

    val fooMessage = ScalaBundle.message("illegal.inheritance.from.sealed.kind", "trait", "Foo")
    val barMessage = ScalaBundle.message("illegal.inheritance.from.sealed.kind", "class", "Bar")

    assertMatches(errorsFromJavaCode(scalaFileText, javaFileText, "Baz")) {
      case Error("Bar", `barMessage`) ::
        Error("Foo", `fooMessage`) ::
        Error("Foo", `fooMessage`) ::
        Nil =>
    }
  }

  def testTypeAliasDeclaration(): Unit = {
    val scalaText =
      """class ManualProbe[I] extends  {
        |  type Self <: ManualProbe[I]
        |
        |  def expectEvent(event: Int): Self = ???
        |}
      """.stripMargin

    val javaText =
      """
        |public class JavaTest {
        |  public static void main(String[] args) {
        |    ManualProbe<String> probe = null;
        |    probe.expectEvent(1).expectEvent(2);
        |  }
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scalaText, javaText, "JavaTest"))
  }

  def testOverrideParameterTypeWithWildcard(): Unit = {
    val java =
      """
        |public interface A<T extends Foo> {
        |    void foo(A<?> a)
        |}
      """.stripMargin
    val scala =
      """
        |class B extends A[Foo] {
        |  def foo(a: A[_]): Unit = ???
        |}
        |
        |trait Foo
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testOverrideRawParameterType(): Unit = {
    val java =
      """
        |public interface A<T extends Foo> {
        |    void foo(A a)
        |}
      """.stripMargin
    val scala =
      """
        |class B extends A[Foo] {
        |  def foo(a: A[_ <: Foo]): Unit = ???
        |}
        |
        |trait Foo
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testWrongOverrideRawParameterType(): Unit = {
    val java =
      """
        |public class A<T extends Foo> {
        |    public void foo(A a)
        |}
      """.stripMargin
    val scala =
      """
        |class B extends A[Foo] {
        |  override def foo(a: A[_]): Unit = ???
        |}
        |
        |trait Foo
      """.stripMargin

    assertMessages(errorsFromScalaCode(scala, java))(
      Error("foo", ScalaBundle.message("member.overrides.nothing", "Method", "foo"))
    )
  }

  def testWrongOverrideParameterTypeWithWildcard(): Unit = {
    val java =
      """
        |public class A<T extends Foo> {
        |    public void foo(A<?> a) {}
        |}
      """.stripMargin
    val scala =
      """
        |class B extends A[Foo] {
        |  override def foo(a: A[_ <: Foo]): Unit = ???
        |}
        |
        |trait Foo
      """.stripMargin

    assertMessages(errorsFromScalaCode(scala, java))(
      Error("foo", ScalaBundle.message("member.overrides.nothing", "Method", "foo"))
    )
  }

  def testRecursiveRawExistential(): Unit = {
    val java =
      """public class Test {
        |    public static interface JwsHeader<T extends JwsHeader<T>> {}
        |
        |    public static class SigningKeyResolverAdapter {
        |        public void resolveSigningKeyBytes(JwsHeader header, String payload) {}
        |    }
        |}""".stripMargin

    val scala =
      """
        |import Test.JwsHeader
        |import Test.SigningKeyResolverAdapter
        |
        |class Resolver extends SigningKeyResolverAdapter {
        |  override def resolveSigningKeyBytes(header: JwsHeader[T] forSome {type T <: JwsHeader[T]}, payload: String): Unit =
        |    super.resolveSigningKeyBytes(header, payload)
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testStaticTraitForwarder(): Unit = {
    val java =
      """public class ExtendTrait implements ScalaTrait {
        |	public int foo(int j) {
        |		return j * 10;
        |	}
        |
        |	public int bar(int k) {
        |		return ScalaTrait$class.bar(this, k);
        |	}
        |}""".stripMargin

    val scala =
      """
        |trait ScalaTrait {
        |  def foo(j: Int): Int
        |
        |  def bar(k: Int): Int = foo(k * 2) + 3
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "ExtendTrait"))
  }

  def testExistentialArgType(): Unit = {
    val scala =
      """package test
        |
        |object ScalaStuff {
        |  trait Stepper[X]
        |  trait StepperShape[S <: Stepper[_]]
        |  class IntStepper extends Stepper[Int]
        |
        |  def stepper[S <: Stepper[_]](implicit ss: StepperShape[S]): S = ???
        |
        |  val intShape: StepperShape[IntStepper] = ???
        |}
      """.stripMargin
    val java =
      """
        |import static test.ScalaStuff.*;
        |
        |public class Test {
        |    public void foo() {
        |        IntStepper stepper = stepper(intShape());
        |    }
        |}""".stripMargin

    assertNothing(errorsFromJavaCode(scala , java, "Test"))
  }

  //example is adapted from com.google.protobuf.AbstractMessageLite
  def testRawTypeSpanTwoClasses(): Unit = {
    val java =
      """
        |public class MessageLite<MessageT extends MessageLite<MessageT, BuilderT>, BuilderT extends MessageLite.Builder<MessageT, BuilderT>> {
        |
        |  public static class Builder<MessageT extends MessageLite<MessageT, BuilderT>, BuilderT extends MessageLite.Builder<MessageT, BuilderT>> {
        |  }
        |
        |  public static MessageLite raw() {
        |    return null;
        |  }
        |}
      """.stripMargin

    val scala =
      """
        |object Test {
        |
        |  val x0 = MessageLite.raw()
        |
        |  val x1: (MessageLite[MT, BT]) forSome {type MT <: MessageLite[MT, BT]; type BT <: MessageLite.Builder[MT, BT]} = MessageLite.raw()
        |
        |}
        |
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testRecursiveRawType(): Unit = {
    val java =
      """
        |public class Bar<T extends Bar> {
        |    public static Bar bar() {
        |        return null;
        |    }
        |}""".stripMargin

    val scala =
      """
        |object Test {
        |  val b: Bar[_ <: Bar[_ <: AnyRef]] = Bar.bar
        |}
        |""".stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testScalaVarargs(): Unit = {
    val java =
      """
        |package foo;
        |import static foo.Foo.a;
        |
        |public class Test {
        |  public static void main(String[] args) {
        |    a(1, 2, 3, 4, 5);
        |  }
        |}
        |""".stripMargin

    val scala =
      """
        |package foo;
        |
        |object Foo {
        |  @scala.annotation.varargs
        |  def a(is: Int*): Unit = ???
        |}
        |""".stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "Test"))
  }

  def testSCL17466(): Unit = {
    val java =
      """
        |package foo;
        |public interface Disposable {
        |    void dispose();
        |
        |    public interface Parent extends Disposable {
        |        void beforeTreeDispose();
        |    }
        |
        |  public static class Application implements Disposable {
        |    @Override
        |    public void dispose() {
        |
        |    }
        |  }
        |
        |  public static class ApplicationManager {
        |    protected static Application ourApplication;
        |
        |    public ApplicationManager() {
        |    }
        |
        |    public static Application getApplication() {
        |        return ourApplication;
        |    }
        |  }
        |}
        |""".stripMargin

    val scala =
      """
        |package foo;
        |object A {
        |  import foo.Disposable.ApplicationManager
        |  val a: Disposable = ApplicationManager.getApplication
        |}""".stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def test17525(): Unit = {
    val java =
      """
        |package foo;
        |public interface I {
        |    String foo(String... args);
        |}""".stripMargin

    val scala =
      """
        |package foo
        |class S extends I {
        |  override def foo(args: String*): String = ???
        |}""".stripMargin
    assertNothing(errorsFromScalaCode(scala, java))
  }
}