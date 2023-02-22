package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class RandomHighlightingBugs extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL13786(): Unit = checkTextHasNoErrors(
    s"""
       |trait Builder {
       |  type Self = this.type
       |  def foo(): Self = this
       |}
       |class Test extends Builder
       |val x: Test = new Test().foo()
       |//true
    """.stripMargin)

  def testSCL14533(): Unit =
    checkTextHasNoErrors(
      """
        |trait Implicit[F[_]]
        |trait Context {
        |  type IO[T]
        |  implicit val impl: Implicit[IO] = new Implicit[IO] {}
        |}
        |class Component[C <: Context](val c: C { type IO[T] = C#IO[T] }) {
        |  import c._
        |  def x(): Unit = {
        |    val a: Implicit[c.IO] = c.impl
        |    val b: Implicit[C#IO] = c.impl
        |  }
        |}
        |
      """.stripMargin)

  def testSCL14700(): Unit =
    checkTextHasNoErrors(
      """
        |type Id[A] = A
        |val opt1: Id[Option[String]] = Some("Foo")
        |opt1.withFilter(one => true).map(one => takesString(one))
        |opt1.filter(one => true).map(one => takesString(one))
        |def takesString(foo: String): Unit = println(foo)
      """.stripMargin
    )

  def testSCL14486(): Unit =
    checkTextHasNoErrors(
      """
        |trait CovariantBifunctorMonad[F[+_, +_]] {
        |  def pure[A](a: A): F[Nothing ,A]
        |  def fail[E](e: E): F[E, Nothing]
        |  def flatMap[E, E1 >: E, A, B](fa: F[E, A], fb: A => F[E1, B]): F[E1, B]
        |}
        |object CovariantBifunctorMonad {
        |  def apply[F[+_, +_]: CovariantBifunctorMonad]: CovariantBifunctorMonad[F] = implicitly
        |  implicit final class Syntax[F[+_, +_]: CovariantBifunctorMonad, E, A](fa: F[E, A]) {
        |    def flatMap[E1 >: E, B](fb: A => F[E1, B]): F[E1, B] = apply[F].flatMap(fa, fb)
        |    def map[B](f: A => B): F[E, B] = flatMap(f(_).pure)
        |  }
        |  implicit final class AnySyntax[A](a: A) {
        |    def pure[F[+_, +_]: CovariantBifunctorMonad]: F[Nothing, A] = apply[F].pure(a)
        |    def fail[F[+_, +_]: CovariantBifunctorMonad]: F[A, Nothing] = apply[F].fail(a)
        |  }
        |}
        |object App {
        |  import CovariantBifunctorMonad._
        |  def error[F[+_, +_] : CovariantBifunctorMonad]: F[Throwable, Unit] = new Throwable{}.fail
        |  def generic[F[+_, +_] : CovariantBifunctorMonad]: F[Throwable, Int] =
        |    for {
        |      i <- 5.pure[F]
        |      _ <- error[F]
        |    } yield i
        |}
      """.stripMargin
    )

  def testSCL14745(): Unit =
    checkTextHasNoErrors(
      """
        |trait Category[F[_, _]] {
        |  def compose[A, B, C](f: F[B, C], g: F[A, B]): F[A, C]
        |}
        |
        |object test {
        |  implicit class OpticOps[T[_, _], A, B](val tab: T[A, B]) extends AnyVal{
        |    def >>>[Q[x, y] >: T[x, y], C](qbc: Q[B, C])(implicit cat: Category[Q]): Q[A, C] =
        |      cat.compose(qbc, tab)
        |  }
        |}
      """.stripMargin
    )

  def testSCL14586(): Unit =
    checkTextHasNoErrors(
      """
        |import scala.language.higherKinds
        |import scala.concurrent.Future
        |
        |case class EitherT[F[_], L, R](var value: F[Either[L, R]])
        |
        |sealed trait Parent
        |class Child extends Parent
        |class AnotherChild extends Parent
        |
        |object VarianceTests extends App {
        |  val future: Future[Either[Child, String]] = ???
        |  val eitherT2: EitherT[Future, Parent, String] = EitherT.apply(future)
        |}
      """.stripMargin
    )

  def testSCL4652(): Unit = checkTextHasNoErrors(
    s"""import scala.language.higherKinds
       |
       |  trait Binding[A]
       |
       |  trait ValueKey[BindingRoot] {
       |    def update(value: Any): Binding[BindingRoot]
       |  }
       |
       |  class Foo[A] {
       |    type ObjectType[B[_]] = B[A]
       |    val bar: ObjectType[Bar] = ???
       |  }
       |
       |  class Bar[A] {
       |    type ValueType = ValueKey[A]
       |    val qux: ValueType = ???
       |  }
       |
       |  object Test {
       |    def foo123(): Unit = {
       |      val g: Foo[String] => Binding[String] = _.bar.qux.update(1)
       |    }
       |  }
       |""".stripMargin
  )

  def testSCL14680(): Unit =
    checkTextHasNoErrors(
      """
        |object IntellijPartialUnification extends App {
        |  import scala.collection.generic.CanBuildFrom
        |  import scala.language.higherKinds
        |  trait X[M[_]]
        |  object X {
        |    implicit def toX[M[_], T](implicit cbf: CanBuildFrom[M[T], T, M[T]]): X[M] = new X[M] {}
        |  }
        |  implicitly[X[List]]
        |}
      """.stripMargin
    )

  def testSCL14468(): Unit =
    checkTextHasNoErrors(
      """
        |object Tag {
        |  type @@[+T, U] = T with Tagged[U]
        |  def tag[U] = new Tagger[U] {}
        |
        |  trait Tagged[U]
        |  trait Tagger[U] {
        |    def apply[T](t: T): T @@ U = ???
        |  }
        |}
        |
        |
        |import Tag._
        |trait TypedId[T] {
        |  type Id = String @@ T
        |}
        |
        |case class Test1(id: Test1.Id)
        |case class Test2(id: Test2.Id)
        |object Test1 extends TypedId[Test1]
        |object Test2 extends TypedId[Test2]
        |
        |
        |
        |object test {
        |  def newId[T](): String @@ T = tag[T][String]("something")
        |
        |  def testFn1(id: Test1.Id = newId()): Unit = { }
        |  def testFn2(id: Test1.Id = newId[Test1]()): Unit = { }
        |  testFn1()
        |  testFn2()
        |}
      """.stripMargin
    )

  def testSCL14897(): Unit = checkTextHasNoErrors(
    """
      |trait Bar
      |trait Foo { this: Bar =>
      |  abstract class TildeArrow[A, B]
      |  implicit object InjectIntoRequestTransformer extends Foo.this.TildeArrow[Int, Int]
      |}
      |
      |class Test extends Foo with Bar {
      |  val foo: Test.this.TildeArrow[Int, Int] = InjectIntoRequestTransformer // expected type error
      |}
    """.stripMargin
  )

  def testSCL14894(): Unit = checkTextHasNoErrors(
    """
      |import Container._
      |
      |object Container {
      |
      |  class Node[A] {
      |
      |    class WithFilter(p: A => Boolean) {
      |      def foreach[U](f: A => U): Unit = {}
      |    }
      |  }
      |}
      |
      |class Container[A] private (root: Node[A]) {
      |
      |  def withFilter(p: A => Boolean): Node[A]#WithFilter = new root.WithFilter(p)
      |}
    """.stripMargin
  )

  def testScl13027(): Unit = {
    checkTextHasNoErrors(
      """
        |object Test {
        |  class returnType[T]
        |
        |  object myObject {
        |    implicit object intType
        |    def myFunction(fun: Int => Unit)(implicit i: intType.type): returnType[Int] = new returnType[Int]
        |
        |    implicit object strType
        |    def myFunction(fun: String => Unit)(implicit i: strType.type): returnType[String] = new returnType[String]
        |  }
        |
        |
        |  (myObject myFunction (_ + 1)): returnType[Int] // compiles, but red "Cannot resolve reference myFunction with such signature"
        |  (myObject myFunction (_.toUpperCase + 1)): returnType[String] // compiles, but red "Cannot resolve reference myFunction with such signature"
        |}
      """.stripMargin)
  }

  def testScl13920(): Unit = {
    checkTextHasNoErrors(
      """
        |trait TBase {
        |  trait TProperty
        |  type Property <: TProperty
        |}
        |
        |trait TSub1 extends TBase {
        |  trait TProperty extends super.TProperty {
        |    def sub1(): String
        |  }
        |  override type Property <: TProperty
        |}
        |
        |trait TSub2 extends TBase {
        |  trait TProperty extends super.TProperty {
        |    def sub2(): String
        |  }
        |  override type Property <: TProperty
        |}
        |
        |trait TSub1AndSub2 extends TSub1 with TSub2 {
        |  trait TProperty extends super[TSub1].TProperty with super[TSub2].TProperty
        |  override type Property <: TProperty
        |}
        |
        |class Sub1AndSub2 extends TSub1AndSub2 {
        |  override type Property = TProperty
        |
        |  case class PropImpl() extends Property {
        |    override def sub1(): String = "sub1"
        |    override def sub2(): String = "sub2"
        |  }
        |
        |  object Property {
        |    def apply(): Property = PropImpl()
        |  }
        |}
      """.stripMargin)
  }

  def testSCL15614(): Unit = checkTextHasNoErrors(
    s"""
       |object Main extends App {
       |  val plot = ((1, 1), 0)
       |  val coef = 1
       |  plot._1._1 * coef
       |}
       |""".stripMargin
  )

  def testSCL15812(): Unit = checkTextHasNoErrors(
    s"""
       |object Test {
       | Seq(("foo" -> ("bar" -> "baz"))).map(_._2._2.length)
       |}
       |""".stripMargin
  )

  //SCL-8236
  def testSCL8236(): Unit = checkTextHasNoErrors(
    """
      |import scala.util.{Try, Failure, Success}
      |class Foo(s: String) {
      |  def doFoo(s: String) = {
      |    doBar1(new Exception {
      |      def doSomething = {
      |        if(true) doBar2(/*start*/Success("", this))
      |        else doBar2(Failure(new Exception()))
      |      }
      |    })
      |  }
      |  def doBar1(e: Exception) = { }
      |  def doBar2(e: Try[(String, Exception)]) = { }
      |}
      |//Success[(String, Exception { def doSomething: Unit })]
      |""".stripMargin.trim,
  )


  //SCL-10077
  def testSCL10077(): Unit = {
    checkTextHasNoErrors(
      """
        |object SCL10077{
        |
        |  trait C[A] {
        |    def test(a: A): String
        |  }
        |  case class Ev[TC[_], A](a: A)(implicit val ev: TC[A]) {
        |    def operate(fn: (A, TC[A]) => Int): Int = 23
        |  }
        |  class A
        |  implicit object AisCISH extends C[A] {
        |    def test(a: A) = "A"
        |  }
        |
        |  val m: Map[String, Ev[C, _]] = Map.empty
        |  val r = m + ("mutt" -> Ev(new A))
        |  val x = r("mutt")
        |
        |  x.operate((arg, tc) => 66)
        |}
      """.stripMargin)
  }

  //SCL-7468
  def testSCL7468(): Unit =
    checkTextHasNoErrors(
      s"""
         |class Container[A](x: A) { def value: A = x }
         |trait Unboxer[A, B] { def unbox(x: A): B }
         |trait LowPriorityUnboxer {
         |  implicit def defaultCase[A, B](implicit fun: A => B) = new Unboxer[A, B] { def unbox(x: A) = fun(x) }
         |}
         |object Unboxer extends LowPriorityUnboxer {
         |  def unbox[A, B](x: A)(implicit f: Unboxer[A, B]) = f.unbox(x)
         |  implicit def containerCase[A] = new Unboxer[Container[A], A] { def unbox(x: Container[A]) = x.value }
         |}
         |implicit def getContained[A](cont: Container[A]): A = cont.value
         |def container[A] = new Impl[A]
         |
         |class Impl[A] { def apply[B](x: => B)(implicit unboxer: Unboxer[B, A]): Container[A] = new Container(Unboxer.unbox(x)) }
         |
         |val stringCont = container("SomeString")
         |val a1 = stringCont
      """.stripMargin,
    )

  //SCL-6372
  def testSCL6372(): Unit =
    checkTextHasNoErrors(
      s"""
         |class TagA[A]
         |  class TagB[B]
         |
         |  abstract class Converter[A: TagA, B: TagB] {
         |    def work(orig: A): B
         |  }
         |  object Converter {
         |    class Detected[A: TagA, B: TagB] {
         |      def using(fun: A => B) = new Converter[A, B] {
         |        def work(orig: A) = fun(orig)
         |      }
         |    }
         |    def apply[A: TagA, B: TagB]() = new Detected
         |  }
         |
         |  class Config[A, B] {
         |    implicit val tagA = new TagA[A]
         |    implicit val tagB = new TagB[B]
         |  }
         |
         |  class Test extends Config[Long, Int] {
         |    val conv = Converter().using(java.lang.Long.bitCount)
         |    def run() = conv.work(366111312291L)
         |  }
      """.stripMargin)

  //SCL-7658
  def testSCL7658(): Unit = {
    checkTextHasNoErrors(
      """implicit def i2s(i: Int): String = i.toString
        |
        |def hoo(x: String): String = {
        |  println(1)
        |  x
        |}
        |def hoo(x: Int): Int = {
        |  println(2)
        |  x
        |}
        |
        |val ss: String = hoo(1)
      """.stripMargin)
  }

  //SCL-8242
  def testSCL8242(): Unit = {
    checkTextHasNoErrors(
      """object SCL8242 {
        |  def foo(x: Float) = {
        |    val t: Double = 56
        |    if (true) x + t
        |    else x
        |  }
        |}
        |""".stripMargin
    )
  }

  //SCL-8242
  def testSCL8242_1(): Unit = {
    checkTextHasNoErrors(
      """import scala.language.implicitConversions
        |import scala.math._
        |
        |object Curve extends App {
        |
        |  case class XY(x: Float, y: Float) {
        |    def unary_- = XY(-x, -y)
        |    def unary_+ = this
        |    def -(xy: XY) = XY(x - xy.x, y - xy.y)
        |    def +(xy: XY) = XY(x + xy.x, y + xy.y)
        |    def *(xy: XY) = XY(x * xy.x, y * xy.y)
        |    def *(f: Float) = XY(x * f, y * f)
        |
        |    def sum = x + y
        |    def dot(xy: XY) = (xy * this).sum
        |    def sizeSquared = this dot this
        |    def size = sqrt(sizeSquared).toFloat
        |
        |    def dist(xy: XY) = (xy - this).size
        |    def dist2(xy: XY) = (xy - this).sizeSquared
        |
        |  }
        |
        |  // allow operations from left side where it makes sense
        |  implicit class XYFloatMaths(val x: Float) extends AnyVal {
        |    def *(xy: XY) = xy * x
        |  }
        |
        |  def lineDistance(v: XY, w: XY, p: XY) = {
        |    val l2 = v dist2 w
        |    if (l2 == 0) p dist2 v
        |    else {
        |      val t = ((p - v) dot (w - v)) / l2
        |      math.sqrt(p dist2 v + t * (w - v))
        |    }
        |  }
        |
        |  val v = XY(0,0)
        |  val w = XY(1,2)
        |  val p = XY(1,1)
        |
        |  println(s"Distance to line ${lineDistance(v,w,p)}")
        |}
        |""".stripMargin
    )
  }


  //SCL-9241
  def testSCL9241(): Unit =
    checkTextHasNoErrors(
      s"""
         |trait Inv[A] { def head: A }
         |trait Cov[+A] { def head: A }
         |
         |def inv(i: Inv[Inv[String]]) = i match {
         |    case l: Inv[a] =>
         |      val x: a = l.head
         |  }
         |//a
      """.stripMargin
    )


  //SCL-12174
  def testSCL12174_1(): Unit =
    checkTextHasNoErrors(
      s"""
         |def foo = (_:String).split(":") match {
         |    case x => x
         |}
      """.stripMargin
    )

  //SCL-4487
  def testSCL4487(): Unit =
    checkTextHasNoErrors(
      s"""
         |def x(a: Int): String => Int = _ match {
         |  case value if value == "0" => a
         |}
      """.stripMargin
    )

  //SCL-5725
  def testSCL5725(): Unit =
    checkTextHasNoErrors(
      """def test(x: List[Int]) = x match {
        |  case l: List[a] => ???
        |}
        |""".stripMargin.trim
    )

  //SCL-5725
  def testSCL5725_1(): Unit =
    checkTextHasNoErrors(
      """
        |class Zoo {
        |  def g: Any = 1
        |  def test = g match {
        |    case l: List[s] =>
        |      l(0)
        |  }
        |}
        |""".stripMargin.trim
    )


  //SCL-9474
  def testSCL9474(): Unit = checkTextHasNoErrors {
    """
      |object Foo {
      |  trait Sys[L <: Sys[L]]
      |
      |  trait SkipMap[E <: Sys[E], A, B] {
      |    def add(entry: (A, B)): Option[B]
      |  }
      |
      |  trait Output[C <: Sys[C]]
      |
      |  class OutputImpl[S <: Sys[S]](proc: Proc[S]) extends Output[S] {
      |    import proc.{outputs => map}
      |
      |    def add(key: String, value: Output[S]): Unit =
      |      map.add(key -> /*start*/value/*end*/)   // type mismatch here
      |  }
      |
      |  trait Proc[J <: Sys[J]] {
      |    def outputs: SkipMap[J, String, Output[J]]
      |  }
      |}
      |""".stripMargin.trim
  }

  //SCL-13634
  def testSCL13634(): Unit =
    checkTextHasNoErrors(
      s"""
         |trait C[+A, B]
         |  type F[T] = C[Int, T]
         |  def foo(f: F[_]): Unit = ???
         |
         |  val st: C[Int, String] = ???
         |  foo(st)
      """.stripMargin
    )

  //SCL-10414
  def testSCL10414(): Unit = {
    checkTextHasNoErrors(
      """object X {
        |  val s1 : Set[Class[_]] = Set()
        |  val s2 : Set[Class[_]] = Set()
        |
        |  if(!s1.union(s2).isEmpty) println(5)
        |}
        |
        |object Y {
        |  val s1 : Set[Class[_]] = Set()
        |  val s2 : Set[java.lang.Class[_]] = Set()
        |
        |  if(!s1.union(s2).isEmpty) println(5)
        |}
        |""".stripMargin
    )
  }

  //SCL-11052
  def testSCL11052(): Unit = {
    checkTextHasNoErrors(
      s"""
         |def second[T]: Seq[T] => Option[T] = _.drop(1).headOption
         |second(Seq("one", "two"))
         |""".stripMargin
    )
  }

  //SCL-6143
  def testSCL6143(): Unit =
    checkTextHasNoErrors(
      """object SCL6143 extends App {
        |  class A {
        |    class B {
        |      def foo: A.this.type = A.this
        |    }
        |  }
        |
        |  class B extends A {
        |    class B extends super[A].B {
        |      val x = super.foo
        |    }
        |    /*start*/(new B().x, new B().foo)/*end*/
        |  }
        |}
        |""".stripMargin
    )

  //SCL-7954
  def testSCL7954(): Unit =
    checkTextHasNoErrors(
      """object SCL7954 {
        |
        |  trait Base {
        |    type HKT[X]
        |    val value: HKT[Int]
        |
        |    def f(n: Int): (Base {type HKT[X] = Base.this.HKT[X]})
        |  }
        |
        |  class Derived(val value: Option[Int]) extends Base {
        |    type HKT[X] = Option[X]
        |
        |    def f(n: Int) = /*start*/new Derived(Some(n * 2))/*end*/
        |  }
        |
        |}
        |""".stripMargin
    )

  //SCL-8234
  def testSCL8234(): Unit =
    checkTextHasNoErrors(
      s"""object Test {
         |  implicit class Gram(number: Double) {
         |    def g: Gram = this
         |
         |    def kg: Gram = Gram(number * 1000)
         |  }
         |
         |  def main(args: Array[String]): Unit = {
         |    ${START}1.kg$END
         |  }
         |}
         |""".stripMargin
    )

  //SCL-9523
  def testScl9523(): Unit = {
    checkTextHasNoErrors(
      s"""import scala.language.{existentials, implicitConversions}
         |
         |object Main extends App {
         |  Tag("key", Set[Value[Value.T]](${START}123$END))
         |}
         |
         |case class Tag(key: String, values: Set[Value[Value.T]])
         |
         |object Value {
         |  type T = X forSome { type X <: AnyVal }
         |
         |  implicit def number2Value(v: Long): Value[T] = LongValue(v)
         |
         |  def apply(v: Long): Value[T] = LongValue(v)
         |}
         |
         |sealed trait Value[+T <: AnyVal] {
         |  def v: T
         |}
         |
         |case class LongValue(v: Long) extends Value[Long]
         |
         |//Value[Value.T]""".stripMargin
    )
  }

  def SCL18853(): Unit = checkTextHasNoErrors(
    """
      |trait Sealed[T] {
      |	type MyType = T
      |}
      |
      |sealed abstract class TestEnum
      |
      |object TestEnum extends Sealed[TestEnum] {
      |
      |	case object STUFF extends MyType
      |	case object FLUFF extends MyType
      |
      |	val all: List[MyType] = List(STUFF, FLUFF)
      |}
      |
      |class Fish(t: TestEnum)
      |
      |object run {
      |	val f = new Fish(TestEnum.FLUFF)
      |}
      |""".stripMargin
  )
}
