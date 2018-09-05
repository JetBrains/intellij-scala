package org.jetbrains.plugins.scala
package annotator


/**
 * Pavel.Fatin, 18.05.2010
 */

class ApplicationAnnotatorTest extends ApplicationAnnotatorTestBase {

  def testEmpty() {
    assertMatches(messages("")) {
      case Nil =>
    }
  }
  
  def testFine() {
    assertMatches(messages("def f(p: Any) {}; f(null)")) {
      case Nil =>
    }
  }
 
  def testDoesNotTakeParameters() {
    assertMatches(messages("def f {}; f(Unit, null)")) {
      case Error("(Unit, null)", "f does not take parameters") :: Nil =>
    }    
  }

  def testMissedParametersClause() {
    assertMatches(messages("def f(a: Any, b: Any) {}; f")) {
      case Error("f", "Missing arguments for method f(Any, Any)") :: Nil =>
    }
  }
  
  def testExcessArguments() {
    assertMatches(messages("def f() {}; f(null, Unit)")) {
      case Error("null", "Too many arguments for method f") ::
              Error("Unit", "Too many arguments for method f") :: Nil =>
    }
  }

  def testMissedParameters() {
    assertMatches(messages("def f(a: Any, b: Any) {}; f()")) {
      case Error("()", "Unspecified value parameters: a: Any, b: Any") ::Nil =>
    }
  }
  
  def testPositionalAfterNamed() {
    assertMatches(messages("def f(a: Any, b: Any, c: Any) {}; f(c = null, null, Unit)")) {
      case Error("null", "Positional after named argument") :: 
              Error("Unit", "Positional after named argument") :: Nil =>
    }
  }
  
  def testNamedDuplicates() {
    assertMatches(messages("def f(a: Any) {}; f(a = null, a = Unit)")) {
      case Error("a", "Parameter specified multiple times") :: 
              Error("a", "Parameter specified multiple times") :: Nil =>
    }
  }
  
  def testUnresolvedParameter() {
    assertMatches(messages("def f(a: Any) {}; f(b = null)")) {
      case Nil =>
    }
  }
  
  def testTypeMismatch() {
    assertMatches(messages("def f(a: A, b: B) {}; f(B, A)")) {
      case Error("B", "Type mismatch, expected: A, actual: B.type") ::
              Error("A", "Type mismatch, expected: B, actual: A.type") ::Nil =>
    }
  }
  
  def testMalformedSignature() {
    assertMatches(messages("def f(a: A*, b: B) {}; f(A, B)")) {
      case Error("f", "f has malformed definition") :: Nil =>
    }
  }
  
  def testIncorrectExpansion() {
    assertMatches(messages("def f(a: Any, b: Any) {}; f(Seq(null): _*, Seq(null): _*)")) {
      case Error("Seq(null): _*", "Expansion for non-repeated parameter") :: 
              Error("Seq(null): _*", "Expansion for non-repeated parameter") :: Nil =>
    }
  }

  def testDoesNotTakeTypeParameters() {
    assertMatches(messages("def f = 0; f[Any]")) {
      case Error("[Any]", "f does not take type parameters") :: Nil =>
    }
  }

  def testMissingTypeParameter() {
    assertMatches(messages("def f[A, B] = 0; f[Any]")) {
      case Error("[Any]", "Unspecified type parameters: B") :: Nil =>
    }
  }

  def testExcessTypeParameter() {
    assertMatches(messages("def f[A] = 0; f[Any, Any]")) {
      case Error("Any", "Too many type arguments for f") :: Nil =>
    }
  }

  def testSCL11227() = {
    val code =
      """
        |object Demo1b {
        |  class Foo[T, F[_]]
        |
        |  def meh[M[_[_]], F[_]](x: M[F]): M[F] = x
        |
        |  meh(new Foo[Int, List])
        |}
        |
        |object Demo1c {
        |  trait TC[T]
        |  class Foo[F[_], G[_]]
        |
        |  def meh[M[_[_]]](x: M[TC]): M[TC] = x
        |
        |  meh(new Foo[TC, TC])
        |}
        |
        |object Demo1d {
        |  trait TC[F[_]]
        |  trait TC2[F[_]]
        |  class Foo[F[_[_]], G[_[_]]]
        |  new Foo[TC, TC2]
        |
        |  def meh[M[_[_[_]]]](x: M[TC2]): M[TC2] = x
        |
        |  meh(new Foo[TC, TC2])
        |}
      """.stripMargin
    assert(messages(code).isEmpty)
  }

  def testSCL11306() = {
    val code =
      """
        |object T {
        |  class A[F[_]]
        |  class B[F[_]]
        |  class C[F[_]]
        |
        |  final case class Prod[F[_[_]], G[_[_]], A[_]](fa: F[A], ga: G[A])
        |
        |  val x = Prod(new A[List], Prod(new B[List], new C[List]))
        |}
      """.stripMargin
    assert(messages(code).isEmpty)
  }

  def testSCL9468A() = {
    val code =
      """
        |trait Foo {
        |  trait Factory {
        |    type Repr[~]
        |
        |    def apply[S](obj: Repr[S]): Any
        |  }
        |
        |  def apply[S](map: Map[Int, Factory], tid: Int, obj: Any): Any =
        |    map.get(tid).fold(???)(f => f(obj.asInstanceOf[f.Repr[S]]))
        |}
      """.stripMargin
    assert(messages(code).isEmpty)
  }

  def testSCL9468B() = {
    val code =
      """
        |class Test {
        |  def c = 1
        |  def c(i: Option[Int]): Int = i.get + 1
        |  def main(args: Array[String]): Unit = {
        |    val a = new A()
        |    a.f(c)
        |  }
        |}
        |
        |class A {
        |  def f[A](m: Option[A] => A) = m(None)
        |}
      """.stripMargin
    assert(messages(code).isEmpty)
  }
}