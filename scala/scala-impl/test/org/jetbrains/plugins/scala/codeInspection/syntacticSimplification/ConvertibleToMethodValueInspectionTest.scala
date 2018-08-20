package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaQuickFixTestBase}

/**
 * Nikolay.Tropin
 * 6/3/13
 */
class ConvertibleToMethodValueInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ConvertibleToMethodValueInspection]

  val description = InspectionBundle.message("convertible.to.method.value.name")
  val hintAnon = InspectionBundle.message("convertible.to.method.value.anonymous.hint")
  val hintEta = InspectionBundle.message("convertible.to.method.value.eta.hint")

  def test_methodCallUntyped() {
    val selected = s"""object A {
                     |  def f(x: Int, y: Int) {
                     |  }
                     |  val f1 = ${START}A.f(_, _)$END
                     |}
                     |"""
    checkTextHasError(selected)
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1 = A.f(_, _)
                 |}"""
    val result = """object A {
                   |  def f(x: Int, y: Int) {
                   |  }
                   |  val f1 = A.f _
                   |}"""
    testQuickFix(text, result, hintAnon)
  }

  def test_infixUntyped() {
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1 = A f (_, _)
                 |}
                 |"""
    checkTextHasNoErrors(text)
  }

  def test_methodCallEtaUntyped() {
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1 = A.f _
                 |}
                 |"""
    checkTextHasNoErrors(text)
  }

  def test_methodCallTyped() {
    val selected = s"""object A {
                       |  def f(x: Int, y: Int) {
                       |  }
                       |  val f1: (Int, Int) => Unit = ${START}A.f(_, _)$END
                       |}"""
    checkTextHasError(selected)
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1: (Int, Int) => Unit = A.f(_, _)
                 |}"""
    val result = """object A {
                   |  def f(x: Int, y: Int) {
                   |  }
                   |  val f1: (Int, Int) => Unit = A.f
                   |}"""
    testQuickFix(text, result, hintAnon)
  }

  def test_methodCallEtaTyped() {
    val selected = s"""object A {
                       |  def f(x: Int, y: Int) {
                       |  }
                       |  val f1: (Int, Int) => Unit = ${START}A.f _$END
                       |}"""
    checkTextHasError(selected)
    val text = """object A {
                 |  def f(x: Int, y: Int) {
                 |  }
                 |  val f1: (Int, Int) => Unit = A.f _
                 |}"""
    val result = """object A {
                   |  def f(x: Int, y: Int) {
                   |  }
                   |  val f1: (Int, Int) => Unit = A.f
                   |}"""
    testQuickFix(text, result, hintEta)
  }

  def test_methodCallWithDefaultUntyped() {
    val selected = s"""object A {
                       |  def f(x: Int, y: Int = 0) {
                       |  }
                       |  val f1 = ${START}A.f(_, _)$END
                       |}"""
    checkTextHasError(selected)
    val text = """object A {
                 |  def f(x: Int, y: Int = 0) {
                 |  }
                 |  val f1 = A.f(_, _)
                 |}"""
    val result = """object A {
                   |  def f(x: Int, y: Int = 0) {
                   |  }
                   |  val f1 = A.f _
                   |}"""
    testQuickFix(text, result, hintAnon)
  }

  def test_methodCallWithDefaultTyped() {
    val text = """object A {
                 |  def f(x: Int, y: Int = 0) {
                 |  }
                 |  val f1: (Int) => Unit = A.f(_)
                 |}
                 |"""
    checkTextHasNoErrors(text)
  }

  def test_infixWithDefaultTyped() {
    val text = """object A {
                 |  def f(x: Int, y: Int = 0) {
                 |  }
                 |  val f1: (Int) => Unit = A f _
                 |}
                 |"""
    checkTextHasNoErrors(text)
  }

  def test_methodCallTypedArgs() {
    val text = """object A {
                 |  def f(x: Any, y: Int = 0) {
                 |  }
                 |  val f1 = A.f(_: Int, _)
                 |}"""
    checkTextHasNoErrors(text)
  }

  def test_infixTypedArgs() {
    val text = """object A {
                 |  def f(x: Any, y: Int = 0) {
                 |  }
                 |  val f1 = A f (_: Int, _: Int)
                 |}"""
    checkTextHasNoErrors(text)
  }

  def test_AbstractExpectedType() {
    val text = """class A {
                 |  def foo() {
                 |    val (x, y) = (0, 1)
                 |    def a1(): Int = 1
                 |    def a2(): Int = 0
                 |    val aa = Map(
                 |      x -> a1 _,
                 |      y -> a2 _
                 |    )
                 |  }
                 |}
                 |"""
    checkTextHasNoErrors(text)
  }

  def test_SCL6000() {
    val text = """class A {
                 |  def inc(f: Int) = f+1
                 |  val set = Set(inc _)
                 |}"""
    checkTextHasNoErrors(text)
  }

  def test_SCL6154() {
    val text =
      """class A {
        |def bar() = {
        |    val x = List(1, 2, 3)
        |    x.map(1 + _)
        |  }
        |}
      """
    checkTextHasNoErrors(text)
  }

  def test_SCL7428() {
    val text =
      """class InspectionTest1[T](translator: T => T = identity[T] _) {
        |  def translate(t: T): T = translator(t)
        |}"""
    checkTextHasNoErrors(text)
  }

  def testImplicits(): Unit = {
    checkTextHasNoErrors(
      """
        |import scala.language.implicitConversions
        |
        |object TupleMethod extends App {
        |
        |  case class Coord(x: Int, y: Int)
        |
        |  class Render {
        |    def lineTo(x: Int, y: Int) = println(s"$x, $y")
        |  }
        |
        |  implicit class ExtRender(val r: Render) extends AnyVal {
        |    def lineTo(xy: Coord) = r.lineTo(xy.x, xy.y)
        |  }
        |
        |  val s = List(Coord(0, 0), Coord(1, 1), Coord(2, 2))
        |
        |  val r = new Render
        |  s.foreach(r.lineTo(_))
        |
        |}
      """.stripMargin)
  }

  def testNonStable(): Unit = {
    checkTextHasNoErrors(
      """class A(s: String) {
        |  def foo(x: String) = x
        |}
        |
        |object Test {
        |  def newA = new A("")
        |  var a = newA
        |
        |  val list = "1" :: "2" :: Nil
        |
        |  list.map(new A("").foo(_))
        |  list.map(newA.foo(_))
        |  list.map(a.foo(_))
        |
        |  list.map(new A("").foo _)
        |  list.map(newA.foo _)
        |  list.map(a.foo _)
        |
        |  class Clazz(var varA: A) {
        |
        |    object objA = new A("")
        |
        |    list.map(varA.foo(_))
        |    list.map(objA.foo(_))
        |  }
        |}
      """.stripMargin
    )
  }

  def testStableFunParam(): Unit = {
    val text = s"""class A(s: String) {
                   |  def foo(x: String) = x
                   |}
                   |
                   |object Test {
                   |  def test(a: A) {
                   |    val list = "1" :: "2" :: Nil
                   |    list.map(${START}a.foo(_)$END)
                   |  }
                   |}
      """.stripMargin
    val result = s"""class A(s: String) {
                     |  def foo(x: String) = x
                     |}
                     |
                     |object Test {
                     |  def test(a: A) {
                     |    val list = "1" :: "2" :: Nil
                     |    list.map(a.foo)
                     |  }
                     |}
      """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, result, hintAnon)
  }

  def testStableVal(): Unit = {
    val text = s"""class A(s: String) {
                   |  def foo(x: String) = x
                   |}
                   |
                   |object Test {
                   |  val newA = new A("")
                   |
                   |  val list = "1" :: "2" :: Nil
                   |
                   |  list.map(${START}newA.foo(_)$END)
                   |}
      """.stripMargin
    val result = s"""class A(s: String) {
                     |  def foo(x: String) = x
                     |}
                     |
                     |object Test {
                     |  val newA = new A("")
                     |
                     |  val list = "1" :: "2" :: Nil
                     |
                     |  list.map(newA.foo)
                     |}
      """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, result, hintAnon)
  }

  def testStableObject(): Unit = {
    val text = s"""class A(s: String) {
                   |  def foo(x: String) = x
                   |}
                   |
                   |object AObj extends A("a") {|
                   |  val list = "1" :: "2" :: Nil
                   |
                   |  list.map(${START}AObj.foo(_)$END)
                   |}
      """.stripMargin
    val result =s"""class A(s: String) {
                    |  def foo(x: String) = x
                    |}
                    |
                    |object AObj extends A("a") {|
                    |  val list = "1" :: "2" :: Nil
                    |
                    |  list.map(AObj.foo)
                    |}
      """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, result, hintAnon)
  }

  def testStableSyntheticFun(): Unit = {
    val text = s"""val list = "a" :: "b" :: Nil
                  |list.filter($START("a" + "b").contains _$END)
               """.stripMargin
    val result = """val list = "a" :: "b" :: Nil
                   |list.filter(("a" + "b").contains)
                 """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, result, hintEta)
  }

  def testFunFromThis(): Unit = {
    val text =
      s"""class A {
         |  def foo(s: String) = s
         |  Seq("aa").map(${START}foo _$END)
         |}
         """.stripMargin
    val result =
      s"""class A {
          |  def foo(s: String) = s
          |  Seq("aa").map(foo)
          |}
         """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, result, hintEta)
  }

  def testCallFromImported(): Unit = {
    val text =
      s"""object A {
        |  def sum(s: String, s2: String) = s + s2
        |}
        |object B {
        |  import A.sum
        |  Seq("aa", "bb").fold("")(${START}sum(_, _)$END)
        |}
      """.stripMargin

    val result =
      s"""object A {
          |  def sum(s: String, s2: String) = s + s2
          |}
          |object B {
          |  import A.sum
          |  Seq("aa", "bb").fold("")(sum)
          |}
      """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, result, hintAnon)
  }

  def testByNameParam(): Unit = {
    val text =
      s"""trait Semigroup[F] {
          |  def zero: F = ???
          |
          |  def append(f1: F, f2: => F): F = ???
          |}
          |
          |object Test {
          |  def foo(i: Iterator[String], s: Semigroup[String]) = i.fold(s.zero)(s.append(_, _))
          |}""".stripMargin
    checkTextHasNoErrors(text)
  }

  def testByNameParam2(): Unit = {
    val text =
      s"""trait Semigroup[F] {
          |  def zero: F = ???
          |
          |  def append(f1: F, f2: => F): F = ???
          |}
          |
          |object Test {
          |  def foo(i: Iterator[String], s: Semigroup[String]) = i.fold(s.zero)(s.append _)
          |}""".stripMargin
    checkTextHasNoErrors(text)
  }

  def testImplicitParameters(): Unit = {
    val text =
      """
        |object Test {
        |  trait Writes[T]
        |  trait JsValue
        |
        |  object Writes {
        |    def toJson[T](o: T)(implicit tjs: Writes[T]): JsValue = ???
        |
        |    implicit val stringWrites: Writes[String] = ???
        |    implicit val intWrites: Writes[Int] = ???
        |  }
        |
        |
        |  Some("some value").map(Writes.toJson(_))
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
