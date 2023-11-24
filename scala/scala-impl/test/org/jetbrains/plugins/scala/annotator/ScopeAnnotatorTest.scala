package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.Message2.Error
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class ScopeAnnotatorTestBase extends SimpleTestCase {

  protected final val Header = "class Foo; class Bar; \n "

  protected def clashesOf(@Language(value = "Scala", prefix = Header) code: String): Seq[String] = {
    val messagesSeq = messages(code)
    messagesSeq.map {
      case error: Error => error.code
      case message =>
        Assert.fail("Unexpected message: " + message).asInstanceOf[Nothing]
    }
  }

  protected def assert2Clashes(/*/*@Language(value = "Scala", prefix = Header)*/*/ code: String, expectedClash: String): Unit = {
    assertClashes(code, expectedClash, expectedClash)
  }

  protected def assertClashes(@Language(value = "Scala", prefix = Header) code: String, expectedClashes: String*): Unit = {
    val actualClashes = clashesOf(code)
    Assert.assertEquals(
      "Incorrect clashed elements",
      expectedClashes.mkString(", "),
      actualClashes.mkString(", ")
    )
  }

  protected def assertFine(/*/*@Language(value = "Scala", prefix = Header)*/*/ code: String): Unit = {
    val clashes = clashesOf(code)
    if (clashes.nonEmpty) {
      Assert.fail("Unexpected clashes: " + clashes.mkString(", "))
    }
  }

  protected def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message2] = {
    val file = (Header + code).parse(scalaVersion)
    implicit val mock: AnnotatorHolderExtendedMock = new AnnotatorHolderExtendedMock(file)
    file.depthFirst().foreach {
      ScopeAnnotator.annotateScope(_)
    }

    mock.annotations.sortBy(_.range.getStartOffset)
  }

  protected def messagesCode(/*/*@Language(value = "Scala", prefix = Header)*/*/ code: String): List[String] =
    messages(code).map(_.code)
}

class ScopeAnnotatorTest_213 extends ScopeAnnotatorTestBase {
  // TODO List of explicit clash groups, report scope
  // ("Foo is already defined as class Foo, object Foo in object Holder")
  // TODO Suggest "rename" quick fix

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_2_13

  def testEmpty(): Unit = {
    assertFine("")
  }

  def testSingleDefinition(): Unit = {
    assertFine("class C")
    assertFine("case class C()")
    assertFine("trait T")
    assertFine("object O")
    assertFine("case object O")
    assertFine("package p {}")
    assertFine("val v = null")
    assertFine("val v")
    assertFine("var v = null")
    assertFine("var v")
    assertFine("type A = Any")
    assertFine("type A")
    assertFine("def f {}")
    assertFine("def f")
    assertFine("def f[T] {}")
    assertFine("def f(p: Any) {}")
    assertFine("(p: Any) => ()")
    assertFine("class C[T]")
    assertFine("class C(p: Any)")
    assertFine("class C(val p: Any)")
    assertFine("class C(var p: Any)")
    assertFine("null match { case p => }")
    assertFine("null match { case a @ _ => }")
    assertFine("for(v <- Nil) {}")
    assertFine("for(x <- Nil; v = null) {}")
    assertFine("{ (v: Any) => }")
    assertFine("class X { def this(x: Any) { this() } }")
  }

  def testDistinctNames(): Unit = {
    assertFine("class A; class B")
    assertFine("case class A(); case class B()")
    assertFine("trait A; trait B")
    assertFine("object A; object B")
    assertFine("case object A; case object B")
    assertFine("package a {}; package b {}")
    assertFine("val a = null; val b = null")
    assertFine("val a, b = null")
    assertFine("val (a, b) = (null, null)")
    assertFine("val a; val b")
    assertFine("var a = null; var b = null")
    assertFine("var a; var b")
    assertFine("type A = Any; type B = Any")
    assertFine("type A; type B")
    assertFine("def a {}; def b {}")
    assertFine("def a; def b")
    assertFine("def f[A, B] {}")
    assertFine("def f(a: Any, b: Any) {}")
    assertFine("def f(a: Any)(b: Any) {}")
    assertFine("(a: Any, b: Any) => ()")
    assertFine("class C[A, B]")
    assertFine("class C(a: Any, b: Any)")
    assertFine("class C(a: Any)(b: Any)")
    assertFine("class C(val a: Any, val b: Any)")
    assertFine("class C(var a: Any, var b: Any)")
    assertFine("(null, null) match { case (a, b) => }")
    assertFine("(null, null) match { case (a @ _, b @ _) => }")
    assertFine("for(a <- Nil; b <- Nil) {}")
    assertFine("for(x <- Nil; a = null; b = null) {}")
    assertFine("for(x <- Nil; b = null) {}")
    assertFine("{ a: Any, b: Any) => }")
  }

  def testNameClash(): Unit = {
    assert2Clashes("class C; class C", "C")
    assert2Clashes("case class C(); case class C()", "C")
    assert2Clashes("trait T; trait T", "T")
    assert2Clashes("object O; object O", "O")
    assert2Clashes("case object O; case object O", "O")
    assertFine("package p {}; package p {}")
    assert2Clashes("val v = null; val v = null", "v")
    assert2Clashes("val v, v = null", "v")
    assert2Clashes("val (v, v) = (null, null)", "v")
    assert2Clashes("val v; val v", "v")
    assert2Clashes("var v = null; var v = null", "v")
    assert2Clashes("var v; var v", "v")
    assert2Clashes("type A = Any; type A = Any", "A")
    assert2Clashes("type A; type A", "A")
    assert2Clashes("def f {}; def f {}", "f")
    assert2Clashes("def f; def f", "f")
    assert2Clashes("def f[T, T] {}", "T")
    assert2Clashes("def f(p: Any, p: Any) {}", "p")
    assert2Clashes("def f(p: Any)(p: Any) {}", "p")
    assert2Clashes("(p: Any, p: Any) => ()", "p")
    assert2Clashes("class C[T, T]", "T")
    assert2Clashes("class C(p: Any, p: Any)", "p")
    assert2Clashes("class C(p: Any)(p: Any)", "p")
    assert2Clashes("class C(val p: Any, val p: Any)", "p")
    assert2Clashes("class C(var p: Any, var p: Any)", "p")
    assert2Clashes("(null, null) match { case (p, p) => }", "p")
    assert2Clashes("(null, null) match { case (a @ _, a @ _) => }", "a")
    assert2Clashes("(null, null) match { case a @ (a @ _, _) => }", "a")
    assertFine("for(v <- Nil; v <- Nil) {}")
    assert2Clashes("for(x <- Nil; v = null; v = null) {}", "v")
    assert2Clashes("for(v <- Nil; v = null) {}", "v")
    assert2Clashes("{ (v: Any, v: Any) => }", "v")
    assert2Clashes("class X { def this(x: Any) { this() }; def this(x: Any) { this() } }", "this")
  }

  def testUnderscore(): Unit = {
    assertFine("val f: (Any => Unit) = { case _: Foo | _: Bar => }")
  }

  // TODO implement processing of distributed package declarations
  //  def testNameClashInPackage {
  //    assertMatches(messages("package p { class C }; package p { class C }")) {
  //      case Error("C", _) :: Error("C", _) :: Nil =>
  //    }
  //  }

  def testThreeClashedNames(): Unit = {
    assertClashes("class C; class C; class C", "C", "C", "C")
    assertClashes("object O; object O; object O", "O", "O", "O")
    assertClashes("trait T; trait T; trait T", "T", "T", "T")
  }

  def testGroups(): Unit = {
    assertClashes("def f(a: Any, a: Any); def f(b: Any, b: Any)", "f", "a", "a", "f", "b", "b")
  }

  def testScopeInspection(): Unit = {
    assert2Clashes("{ class C; class C}", "C")
    assert2Clashes("class X { class C; class C }", "C")
    assert2Clashes("case class X() { class C; class C }", "C")
    assert2Clashes("trait X { class C; class C }", "C")
    assert2Clashes("object X { class C; class C }", "C")
    assert2Clashes("case object X { class C; class C }", "C")
    assert2Clashes("package X { class C; class C }", "C")
    assert2Clashes("def X { class C; class C }", "C")
    assert2Clashes("val X = { class C; class C }", "C")
    assert2Clashes("var X = { class C; class C }", "C")
    assert2Clashes("for(x <- Nil) { class C; class C }", "C")
    assert2Clashes("if(true) { class C; class C }", "C")
    assert2Clashes("if(true) {} else { class C; class C }", "C")
    assert2Clashes("while(true) { class C; class C }", "C")
    assert2Clashes("do { class C; class C } while(true)", "C")
    assert2Clashes("try { class C; class C } catch { case _ => }", "C")
    assert2Clashes("try {} catch { case _ => } finally { class C; class C }", "C")
    assert2Clashes("new { class C; class C }", "C")
    assert2Clashes("null match { case _ => class C; class C }", "C")
    assert2Clashes("val x: (Any => Unit) = { case _ => class C; class C }", "C")
    assert2Clashes("for(x <- Nil) { class C; class C }", "C")
    assert2Clashes("{ (x: Any) => class C; class C }", "C")
  }

  def testScopeBoundary(): Unit = {
    assertFine("class C; { class C }")
    assertFine("class C; class X { class C }")
    assertFine("class C; case class X() { class C }")
    assertFine("class C; trait X { class C }")
    assertFine("class C; object X { class C }")
    assertFine("class C; case object X { class C }")
    assertFine("class C; package X { class C }")
    assertFine("class C; def X { class C }")
    assertFine("class C; val X = { class C }")
    assertFine("class C; var X = { class C }")
    assertFine("val v = null; type X = { val v = null }")
    assertFine("class C; for(x <- Nil) { class C }")
    assertFine("class C; if(true) { class C } else { class C }")
    assertFine("class C; while(true) { class C }")
    assertFine("class C; do { class C } while(true)")
    assertFine("class C; try { class C } catch { case _ => } finally { class C }")
    assertFine("class C; new { class C }")
    assertFine("class C; for(x <- Nil) { class C }")
    assertFine("class C; val x: C forSome { type C <: Any } = null")
  }

  def testScopeBoundaryParameters(): Unit = {
    assertFine("class C; class X[C]")
    assertFine("val v = null; class X(v: Any)")
    assertFine("class C; def X[C] {}")
    assertFine("val v = null; def X(v: Any) {}")
    assertFine("val v = null; null match { case v => }")
    assertFine("null match { case v =>; case v => }")
    assertFine("val v = null; val x: (Any => Unit) = { case v => }")
    assertFine("val x: (Any => Unit) = { case v =>; case v => }")
    assertFine("val v = null; for(v <- Nil) {}")
    assertFine("val v = null; for(x <- Nil; v = null) {}")
  }

  def testSelfBoundary(): Unit = {
    assertFine("class C { class C }")
    assertFine("class C[T] { class T }")
    assertFine("def f { val f = null }")
    assertFine("def x[T] { class T }")
    assertFine("def x(p: Any) { val p = null }")
    assertFine("val v = { val v = null }")
    assertFine("var v = { val v = null }")
    assertFine("for(v <- Nil) { val v = null }")
    assertFine("for(x <- Nil; v = null) { val v = null }")
    assertFine("null match ( case v => val v = null }")
    assertFine("{ (v: Any) => val v = null }")
  }

  def testNestedScopes(): Unit = {
    assertFine("class C; { class C; { class C } }")
    assert2Clashes("class X; { class X; { class C; class C } }", "C")
  }

  def testSameLeveScopeBoundary(): Unit = {
    assertFine("{ class C }; { class C }")
  }

  def testMembers(): Unit = {
    assert2Clashes("class C(p: Any) { val p = null }", "p")
    assertClashes("class C(a: Any, b: Any) { val a = null; val b = null }", "a", "b", "a", "b")
    assertClashes("class C(a: Any)(b: Any) { val b = null; val a = null }", "a", "b", "b", "a")
    assert2Clashes("class C(val p: Any) { val p = null }", "p")
    assert2Clashes("class C(var p: Any) { val p = null }", "p")
    assert2Clashes("case class C(p: Any) { val p = null }", "p")
    assert2Clashes("case class C(val p: Any) { val p = null }", "p")
    assert2Clashes("case class C(var p: Any) { val p = null }", "p")
  }

  def testMembersCrossClash(): Unit = {
    assertClashes("class C(p: Any, p: Any) { val p = null }", "p", "p", "p")
  }

  def testMemberAndIds(): Unit = {
    assertFine("class X(p: Any){ class p }")
    assert2Clashes("class X(p: Any){ val p = null }", "p")
    assert2Clashes("class X(p: Any){ object p }", "p")
    assert2Clashes("class X(p: Any){ case class p() }", "p")
  }

  def testTypesClash(): Unit = {
    assert2Clashes("class T; trait T", "T")
    assert2Clashes("class T; type T = Any", "T")
    assert2Clashes("class T; type T", "T")
    assert2Clashes("class T; case class T()", "T")
  }

  def testTermsClash(): Unit = {
    assert2Clashes("def v {}; def v", "v")
    assert2Clashes("def v {}; val v = null", "v")
    assert2Clashes("def v {}; val v", "v")
    assert2Clashes("def v {}; var v = null", "v")
    assert2Clashes("def v {}; var v", "v")
    assert2Clashes("def v {}; object v", "v")
    assert2Clashes("def v {}; case class v()", "v")
  }

  def testTypeAndTerms(): Unit = {
    assertFine("class x; val x = null")
    assertFine("class x; val x")
    assertFine("class x; var x = null")
    assertFine("class x; var x")
    assertFine("class x; def x {}")
    assertFine("class x; def x")
    assertFine("class x; object x")
    assertFine("class x; case object x")
  }

  def testTypesOrTermsClash(): Unit = {
    assert2Clashes("class X; class X; object X", "X")
    assert2Clashes("object X; object X; class X", "X")
  }

  def testCaseClassCompanion(): Unit = {
    assertFine("case class X(); object X")
    assertFine("case class X(); case object X")
    assert2Clashes("case class v(); def v {}", "v")
    assertClashes("case class X(); object X; def X {}", "X",  "X")
    assert2Clashes("case class X(); object X; object X", "X")
    assert2Clashes("case class X(); case class X(); object X", "X")
  }

  def testFunctionParameterNames(): Unit = {
    assert2Clashes("def f(foo: Any) {}; def f(bar: Any) {}", "f")
  }

  def testFunctionSignature(): Unit = {
    assertFine("def f() {}; def f(p: Any) {}")
    assertFine("def a(p: Any) {}; def b(p: Any) {}")
    assertFine("def f(p: Bar) {}; def f(p: Foo) {}")
    assertFine("def f(a: Any) {}; def f(a: Any, b: Any) {}")
    assertFine("def f(a: Bar)(b: Any) {}; def f(a: Foo)(b: Any) {}")
    assertFine("def f(a: Any, b: Any) {}; def f(a: Any)(b: Any) {}")

    assert2Clashes("def f {}; def f {}", "f")
    assert2Clashes("def f() {}; def f() {}", "f")
    assert2Clashes("def f {}; def f() {}", "f")
    assert2Clashes("def f(p: Any) {}; def f(p: Any) {}", "f")
    assert2Clashes("def f(a: Any) {}; def f(b: Any) {}", "f")
    assert2Clashes("def f(a: Any, b: Any) {}; def f(a: Any, b: Any) {}", "f")
    assert2Clashes("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any) {}", "f")
  }

  //TODO thoughtfully consider type parameters and return type
  // (all functions with type parameters are simply excluded now)
  def testFunctionTypeParameters(): Unit = {
    assertFine("def f[A] = new Foo; def f[A, B] = new Bar")

    //    assert2Clashes("def f[A] = new Foo; def f[A] = new Bar", "f")
    //    assert2Clashes("def f[A] = new Foo; def f[B] = new Bar", "f")
    //    assert2Clashes("def f[A] = new Foo; def f[A, B] = new Foo", "f")
    //
    assertFine("def f = new Foo; def f[A] = new Bar")
    //    assert2Clashes("def f = new Foo; def f[A] = new Foo", "f")
    //
    assertFine("val f = new Foo; def f[A] = new Bar")
    //    assert2Clashes("val f = new Foo; def f[A] = new Foo", "f")
  }

  def testForStatement(): Unit = {
    assertFine("for (a <- Some(1); a <- Some(a)) {}")
    assertFine("for {a <- Some(1); a <- Some(a)} {}")
    assertFine("for {a <- Some(1); b <- Some(a); a = b} {}")
    assert2Clashes("for {a <- Some(1); a <- Some(a); b = a; a = b} {}", "a")
  }

  def testRepeatedParameter(): Unit = {
    assertFine("def f(p: Any*) {}; def f(p: Any) {}")
    assertFine("def f(p: Any) {}; def f(p: Any*) {}")
    assert2Clashes("def f(p: Any*) {}; def f(p: Any*) {}", "f")
  }

  def testConstructorSignature(): Unit = {
    assertFine("class X { def this(x: Int) = this(); def this(x: AnyVal) = this() }")
    assertFine("class X { def this(a: Any) = this(); def this(a: Any, b: Any) = this() }")
  }

  def testPrimaryConstructorAndSecondaryConstructor(): Unit = {
    assertFine("class X(x: Foo) { def this(x: Bar) {} }")
    assertFine("class X(a: Any) { def this(a: Any, b: Any) {} }")
    assertFine("class X(a: Any) { def this(a: Any)(b: Any) {} }")

    // TODO find clashes with primary constructor
    //    assert2Clashes("class X { def this() {} }", "this")
    //    assert2Clashes("class X(x: Any) { def this(x: Any) {} }", "this")
    //    assert2Clashes("class X(a: Any, b: Any) { def this(a: Any, b: Any) {} }", "this")
    //    assert2Clashes("class X(a: Any)(b: Any) { def this(a: Any)(b: Any) {} }", "this")
  }

  def testDuplicatedPrimaryConstructorAndFieldMembers(): Unit = {
    assertClashes(
      """class MyClass3(name: String) {
        |  val name: String = ???
        |}""".stripMargin,
      "name", "name",
    )
    assertClashes(
      """class MyClass4(name: String)(name: String) {
        |  val name: String = ???
        |  val name: String = ???
        |}""".stripMargin,
      "name", "name", "name", "name",
    )
  }

  def testDuplicatedPrimaryConstructorParameters(): Unit = {
    assertClashes("""class MyClass1(name: String, name: String)(name: String)""", "name", "name", "name")
  }

  def testDuplicatedFieldMembers(): Unit = {
    assert2Clashes(
      """class MyClass2 {
        |  val name: String = ???
        |  val name: String = ???
        |}
        |""".stripMargin,
      "name"
    )
  }

  def testFunctionHolders(): Unit = {
    assertFine("class X { def f() {}; def f(p: Any) {} }")
    assertFine("object X { def f() {}; def f(p: Any) {} }")
    assertFine("trait X { def f() {}; def f(p: Any) {} }")
    assertFine("new { def f() {}; def f(p: Any) {} }")

    assert2Clashes("def x { def f() {}; def f(p: Any) {} }", "f")
    assert2Clashes("if(true) { def f() {}; def f(p: Any) {} }", "f")
    assert2Clashes("if(true) {} else { def f() {}; def f(p: Any) {} }", "f")
    assert2Clashes("while(true) { def f() {}; def f(p: Any) {} }", "f")
    assert2Clashes("do { def f() {}; def f(p: Any) {} } while(true)", "f")
    assert2Clashes("for(x <- Nil) { def f() {}; def f(p: Any) {} }", "f")
  }

  def testLocalFunctionSignature(): Unit = {
    assert2Clashes("def x { def f() {}; def f(p: Any) {} }", "f")
    assert2Clashes("def x { def f(p: Foo) {}; def f(p: Bar) {} }", "f")
    assert2Clashes("def x { def f(a: Any) {}; def f(a: Any, b: Any) {} }", "f")
    assert2Clashes("def x { def f(a: Any) {}; def f(a: Any)(b: Any) {} }", "f")
    assert2Clashes("def x { def f(a: Any)(b: Foo) {}; def f(a: Any)(b: Bar) {} }", "f")
  }

  def testFunctionFollowingApplications(): Unit = {
    assertFine("def f(a: Any) {}; def f(a: Any)(b: Any) {}")
    assertFine("def f(a: Any)(b: Foo) {}; def f(a: Any)(b: Bar) {}")
    assertFine("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any, c: Any) {}")
    assertFine("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any)(c: Any) {}")

    assertFine("def f(a: Any)(b: Any) {}; def f(a: Any, b: Any) {}")

    assert2Clashes("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any) {}", "f")
    assert2Clashes("def f(a: Any)(b: Any)(c: Any) {}; def f(a: Any)(b: Any)(c: Any) {}", "f")
  }

  def testConstructorFollowingApplications(): Unit = {
    assertFine(
      """class X {
        |  def this(a: Any) = this()
        |  def this(a: Any)(b: Any) = this()
        |}""".stripMargin)
    assertFine(
      """class X {
        |  def this(a: Any)(b: Any) = this()
        |  def this(a: Any)(b: Any, c: Any) = this()
        |}""".stripMargin)
    assertFine(
      """class X {
        |  def this(a: Any)(b: Any) = this()
        |  def this(a: Any)(b: Any)(c: Any) = this()
        |}""".stripMargin)

    assertFine(
      """class X {
        |  def this(a: Any)(b: Any) = this()
        |  def this(a: Any, b: Any) = this()
        |}""".stripMargin)

    assertFine(
      """class X {
        |  def this(a: Any)(b: Int) = this()
        |  def this(a: Any)(b: Long) = this()
        |}""".stripMargin)

    assert2Clashes(
      """class X {
        |  def this(a: Any)(b: Any) = this()
        |  def this(a: Any)(b: Any) = this()
        |}""".stripMargin, "this")

    assert2Clashes(
      """class X {
        |  def this(a: Any)(b: Any)(c: Any) = this()
        |  def this(a: Any)(b: Any)(c: Any) = this()
        |}""".stripMargin, "this")
  }

  def testTypeErasure(): Unit = {
    // precheck
    assertFine("def f(a: Foo) {}; def f(a: Bar) {}")
    assert2Clashes("class Holder[T]; def f(a: Holder) {}; def f(a: Holder) {}", "f")
    assert2Clashes("class Holder[T]; def f(a: Holder[Any]) {}; def f(a: Holder[Any]) {}", "f")

    assert2Clashes("class Holder[T]; def f(a: Holder[Foo]) {}; def f(a: Holder[Bar]) {}", "f")
    assert2Clashes("class Holder[T]; def f(a: Holder[Holder[Foo]]) {}; def f(a: Holder[Holder[Bar]]) {}", "f")
    assert2Clashes("class Holder[T]; def f(a: Holder[Foo], b: Holder[Bar]) {}; def f(a: Holder[Bar], b: Holder[Foo]) {}", "f")
    assert2Clashes("class Holder[A, B]; def f(a: Holder[Foo, Bar]) {}; def f(a: Holder[Bar, Foo]) {}", "f")

    assert2Clashes("class ArrayFoo[T]; def f(a: ArrayFoo[Foo]) {}; def f(a: ArrayFoo[Bar]) {}", "f")
    assert2Clashes("class FooArray[T]; def f(a: FooArray[Foo]) {}; def f(a: FooArray[Bar]) {}", "f")
  }

  def testEarlyDefinitions(): Unit = {
    assertFine("new { val a = 1} with AnyRef; new { val a = 1} with AnyRef")
    assert2Clashes("new { val a = 1; val a = 2} with AnyRef", "a")
  }

  def testAnonymousClassDefinition(): Unit = {
    assertFine("new Object { val a = 1; } ; new Object { val a = 2; }")
    assert2Clashes("new Object { val a = 1; val a = 2 }", "a")
  }

  def testCaseClause(): Unit = {
    assert2Clashes("{case (a, a) => ()}", "a")
    assert2Clashes("{case (a, (b, a)) => ()}", "a")
    assert2Clashes("{case a | a => ()}", "a")
    assertFine("{case a => val a = 1}")
    assertFine("{case a => {(); val a = 1}}")
  }

  def testListOfPatterns(): Unit = {
    assert2Clashes("val (a, a) = ()", "a")
    assert2Clashes("val (a, (b, a)) = ()", "a")
  }

  def testBackticks(): Unit = {
    Assert.assertTrue(clashesOf("class Test1 { def foo = ???; def `foo` = ???}").toSet == Set("foo", "`foo`"))
    Assert.assertTrue(clashesOf("class A; trait `A`").toSet == Set("A", "`A`"))
  }

  def testFunctionSignatureTypeConformanceAndErasure(): Unit = {
    assertClashes("type Alias = Any; def f(p: Any) {}; def f(p: Alias) {}", "f",  "f")
    //Any and AnyVal are erased to Object
    assertClashes("def f(p: Any) {}; def f(p: AnyVal) {}", "f",  "f")
  }

  def testMessagesTextAndRange(): Unit = {
    def assertMessagesText(code: String, expectedMessagesText: String): Unit = {
      val messagesText = messages(code).map(_.textWithRangeAndMessage).mkString("\n")
      assertEquals(expectedMessagesText, messagesText)
    }

    messages("class Foo; class Foo")

    assertMessagesText(
      "class Foo; class Foo",
      """Error((6,9),Foo is already defined in the scope)
        |Error((30,33),Foo is already defined in the scope)
        |Error((41,44),Foo is already defined in the scope)""".stripMargin
    )
    assertMessagesText(
      "def f(a: Any, b: Any) {}; def f(a: Any, b: Any) {}",
      """Error((28,29),f(AnyRef, AnyRef)Unit is already defined in the scope)
        |Error((54,55),f(AnyRef, AnyRef)Unit is already defined in the scope)""".stripMargin
    )
    assertMessagesText(
      "def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any) {}",
      """Error((28,29),f(AnyRef)(AnyRef)Unit is already defined in the scope)
        |Error((54,55),f(AnyRef)(AnyRef)Unit is already defined in the scope)""".stripMargin
    )
    assertMessagesText(
      "def x { def f(p: Any) {}; def f(p: Any) {} }",
      """Error((36,37),f is already defined in the scope)
        |Error((54,55),f is already defined in the scope)""".stripMargin
    )
    assertMessagesText(
      "def x { def f(a: Any) {}; def f(a: Any, b: Any) {} }",
      """Error((36,37),f is already defined in the scope)
        |Error((54,55),f is already defined in the scope)""".stripMargin
    )
  }

  //SCL-7116
  def testDifferentReturnType(): Unit = {
    assertFine("""class Option[+X]; def foo(x: Option[Foo]): Foo = {}; def foo(y: Option[Bar]): Bar = {}""")

    assert2Clashes("""class Option[+X]; def foo(x: Option[Foo]): Bar = {}; def foo(y: Option[Bar]): Bar = {}""", "foo")
    assert2Clashes("""class Option[+X]; def foo(x: Option[Foo]): Option[Foo] = {}; def foo(y: Option[Bar]): Option[Bar] = {}""", "foo")
  }

  def testByNameParameter(): Unit = {
    //SCL-8633
    assertFine("def a(x: Int) = x; def a(x: => Int) = x")

    //SCL-15358
    assertFine(
      """
        |class Test {
        |  protected def test(i: Int, a: Any): Unit = ???
        |  def test(i: Int, a: => Any): Unit = ???
        |}
      """.stripMargin)
  }

  def testPrivateField(): Unit = {
    //SCL-2101
    assertFine(
      """class Some(name: Int) {
        |    def name {""}
        |}""".stripMargin)

    //SCL-5789
    assertFine(
      """
        |class Test {
        |  private[this] val x = 1
        |  def x(): String = this.x.toString
        |}
      """.stripMargin)

    //SCL-5869
    assertFine(
      """
        |class Test(private[this] var param:Int) {
        |  def param():Int = this.param
        |  def param_= (param:Int) {this.param = param}
        |}
      """.stripMargin)

    assertFine("class A(private[this] val param: Int) { def param(): Int = this.param }")
    assertFine("class A(param: Int) { def param(): Int = this.param }")
    assertFine("class A(param: Int) { def param: Int = 1 }")
    assertFine("class A() { private[this] val x: Int = 1; def x(): Int = x }")
    assertFine("class A() { private[this] var x: Int = 1; def x(): Int = x }")
    assertFine("class A() { private[this] var x: Int = 1; def x: Int = 1 }")

    assert2Clashes("class A() { private var x: Int = 1; def x(): Int = x }", "x")
    assert2Clashes("class A(private val param: Int) { def param(): Int = this.param }", "param")
    assert2Clashes("class A(val param: Int) { def param(): Int = this.param }", "param")
  }

  //methods in structural types may be overloaded, but return type is ignored
  def testStructuralType(): Unit = {
    assertFine(
      """
        |object Test {
        |  trait A[T]
        |
        |  type T = {
        |    def foo(f: A[Int])
        |    def foo(f: A[Boolean])
        |  }
        |}
      """.stripMargin)

    assert2Clashes(
      """object Test {
        |  type MyInt = Int
        |
        |  type T = {
        |    def foo(f: Int)
        |    def foo(f: MyInt)
        |  }
        |}
      """.stripMargin, "foo")

    assert2Clashes(
      """
        |object Test {
        |  type T = {
        |    def foo(): Int
        |    val foo: Int
        |  }
        |}
      """.stripMargin, "foo")
  }

  def testDifferentReturnTypes(): Unit = {
    assert2Clashes(
      """
        |object Test {
        |  def test(a: Int): Int = 0
        |  def test(b: Int): Boolean = true
        |}
      """.stripMargin, "test")

    assertFine(
      """
        |trait Option[+X]
        |object Test {
        |  def test(a: Option[Int]): Int = 0
        |  def test(b: Option[Boolean]): Boolean = true
        |}
      """.stripMargin)

    assert2Clashes(
      """
        |trait Option[+X]
        |object Test {
        |  type Opt[Y] = Option[Y]
        |  def test(a: Opt[Int]): Int = 0
        |  def test(b: Option[Int]): Boolean = true
        |}
      """.stripMargin, "test")

  }
}

class ScopeAnnotatorTest_3 extends ScopeAnnotatorTest_213 {

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3

  override protected def assertFine(@Language("Scala 3") code: String): Unit = super.assertFine(code)
  override protected def assertClashes(/*/*@Language("Scala 3")*/*/ code: String, expectedClashes: String*): Unit = super.assertClashes(code, expectedClashes: _*)
  override protected def assert2Clashes(@Language("Scala 3") code: String, expectedClash: String): Unit = super.assert2Clashes(code, expectedClash)

  def testExtensions(): Unit = {
    assertFine(
      """extension (n: Int)
        |  def mySpecialToString: String = n.toString
        |  def mySpecialMkString(prefix: String, separator: String, postfix: String): String =
        |    List(n).mkString(prefix, separator, postfix)
        |
        |extension (n: Long)
        |  def mySpecialToString: String = n.toString
        |  def mySpecialMkString(prefix: String, separator: String, postfix: String): String =
        |    List(n).mkString(prefix, separator, postfix)
        """.stripMargin,
    )
  }

  def testEnumFine(): Unit = {
    assertFine("enum MyEnum { case MyCase1 }")
    assertFine("enum MyEnum(name: Int) { case MyCase1 extends MyEnum(23) }")
    assertFine("enum MyEnum1 { case MyCase1 } ; enum MyEnum2 { case MyCase1 }")
  }

  def testEnumClash(): Unit = {
    assert2Clashes("enum MyEnum { case MyCase1 } ; enum MyEnum { case MyCase1 }", "MyEnum")
  }

  def testEnumCaseClash(): Unit = {
    assert2Clashes("enum MyEnum { case MyCase1, MyCase2, MyCase1 }", "MyCase1")
    assert2Clashes("enum MyEnum { case MyCase1 ; case MyCase2 ; case MyCase1 }", "MyCase1")
  }

  def testEnumDuplicatedPrimaryConstructorAndFieldMembers(): Unit = {
    assertClashes(
      """enum MyEnum(name: Int) {
        |  val name: Int = ???
        |  case MyCase1 extends MyEnum(42)
        |}""".stripMargin,
      "name", "name",
    )
    assertClashes(
      """enum MyEnum(name: Int)(name: Int) {
        |  val name: Int = ???
        |  val name: Int = ???
        |  case MyCase1 extends MyEnum(42)(23)
        |}""".stripMargin,
      "name", "name", "name", "name",
    )
  }

  def testEnumDuplicatedPrimaryConstructorParameters(): Unit = {
    assertClashes(
    """enum MyEnum(name: Int, name: Int)(name: Int) { case MyCase1 extends MyEnum(1, 2)(3) }""",
      "name", "name", "name"
    )
  }

  def testEnumDuplicatedFieldMembers(): Unit = {
    assertClashes(
      """enum MyEnum {
        |  val name: Int = ???
        |  val name: Int = ???
        |  case MyCase
        |}
        |""".stripMargin,
      "name", "name"
    )
  }

  def testTraitDuplicatedPrimaryConstructorAndFieldMembers(): Unit = {
    assertClashes(
      """trait MyTrait(name: Int) {
        |  val name: Int = ???
        |}""".stripMargin,
      "name", "name",
    )
    assertClashes(
      """trait MyTrait(name: Int)(name: Int) {
        |  val name: Int = ???
        |  val name: Int = ???
        |}""".stripMargin,
      "name", "name", "name", "name",
    )
  }

  def testTraitDuplicatedPrimaryConstructorParameters(): Unit = {
    assertClashes(
    """trait MyTrait(name: Int, name: Int)(name: Int)""",
      "name", "name", "name"
    )
  }

  def testTraitDuplicatedFieldMembers(): Unit = {
    assertClashes(
      """trait MyTrait {
        |  val name: Int = ???
        |  val name: Int = ???
        |}
        |""".stripMargin,
      "name", "name"
    )
  }

  def testMultipleAnonymousParameters(): Unit = {
    assertFine(
      """case class Company(name: String)
        |case class SalesRep(name: String)
        |
        |case class Invoice(customer: String)(using Company, SalesRep):
        |  override def toString = s"${summon[Company].name} / ${summon[SalesRep].name} - Customer: $customer"
        |
        |@main def test(): Unit =
        |  given Company = Company("Big Corp")
        |  given SalesRep = SalesRep("John")
        |  println(Invoice("Peter LTD"))
        |""".stripMargin
    )
  }
}