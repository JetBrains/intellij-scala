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

    assertMatches(messages("def f(p: Any) {}; f(null, Unit)")) {
      case Error("Unit", "Too many arguments for method f(Any)") :: Nil =>
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

  def testNonApplicable(): Unit = {
    assertMatches(messages("object Test; Test()")) {
      case Error("()", "'Test.type' does not take parameters") :: Nil =>
    }

    assertMatches(messages("3()")) {
      case Error("()", "'Int' does not take parameters") :: Nil =>
    }

    assertMatches(messages("val a: Any = ???; a(3)")) {
      case Error("(3)", "'a.type' does not take parameters") :: Nil =>
    }
  }

  def testNonApplicableInUpdate(): Unit = {
    assertMatches(messages("object Test; Test(3) = 3")) {
      case Error("(3)", "'Test.type' does not take parameters") :: Nil =>
    }
  }
}