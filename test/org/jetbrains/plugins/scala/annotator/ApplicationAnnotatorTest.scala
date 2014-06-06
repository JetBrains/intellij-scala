package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import lang.psi.api.base.ScReferenceElement
import lang.psi.types.Compatibility
import lang.psi.api.toplevel.typedef.ScClass
import lang.psi.api.expr.ScMethodCall
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel.Fatin, 18.05.2010
 */

class ApplicationAnnotatorTest extends SimpleTestCase {
  final val Header = """
  class Seq[+A] 
  object Seq { def apply[A](a: A) = new Seq[A] } 
  class A; class B; 
  object A extends A; object B extends B
  """ 
  
  def testEmpty {
    assertMatches(messages("")) {
      case Nil =>
    }
  }
  
  def testFine {
    assertMatches(messages("def f(p: Any) {}; f(null)")) {
      case Nil =>
    }
  }
 
  /*def testDoesNotTakeParameters {
    assertMatches(messages("def f {}; f(Unit, null)")) {
      case Error("(Unit, null)", "f does not take parameters") :: Nil =>
    }    
  }*/
  
  def testMissedParametersClause {
    assertMatches(messages("def f(a: Any, b: Any) {}; f")) {
      case Error("f", "Missing arguments for method f(Any, Any)") ::
              Error("f", "Cannot resolve reference f with such signature") :: Nil =>
    }
  }
  
  def testExcessArguments {
    assertMatches(messages("def f() {}; f(null, Unit)")) {
      case Error("null", "Too many arguments for method f") ::
              Error("f", "Cannot resolve reference f with such signature") ::
              Error("Unit", "Too many arguments for method f") ::
              Error("f", "Cannot resolve reference f with such signature") :: Nil =>
    }
  }

  def testMissedParameters {
    assertMatches(messages("def f(a: Any, b: Any) {}; f()")) {
      case Error("()", "Unspecified value parameters: a: Any, b: Any") ::
              Error("f", "Cannot resolve reference f with such signature") ::Nil =>
    }
  }
  
  def testPositionalAfterNamed {
    assertMatches(messages("def f(a: Any, b: Any, c: Any) {}; f(c = null, null, Unit)")) {
      case Error("null", "Positional after named argument") :: 
              Error("Unit", "Positional after named argument") :: Nil =>
    }
  }
  
  def testNamedDuplicates {
    assertMatches(messages("def f(a: Any) {}; f(a = null, a = Unit)")) {
      case Error("a", "Parameter specified multiple times") :: 
              Error("a", "Parameter specified multiple times") :: Nil =>
    }
  }
  
  def testUnresolvedParameter {
    assertMatches(messages("def f(a: Any) {}; f(b = null)")) {
      case Nil =>
    }
  }
  
  def testTypeMismatch {
    assertMatches(messages("def f(a: A, b: B) {}; f(B, A)")) {
      case Error("B", "Type mismatch, expected: A, actual: B.type") ::
              Error("f", "Cannot resolve reference f with such signature") ::
              Error("A", "Type mismatch, expected: B, actual: A.type") ::
              Error("f", "Cannot resolve reference f with such signature") ::Nil =>
    }
  }
  
  def testMalformedSignature {
    assertMatches(messages("def f(a: A*, b: B) {}; f(A, B)")) {
      case Error("f", "f has malformed definition") :: Nil =>
    }
  }
  
  def testIncorrectExpansion {
    assertMatches(messages("def f(a: Any, b: Any) {}; f(Seq(null): _*, Seq(null): _*)")) {
      case Error("Seq(null): _*", "Expansion for non-repeated parameter") :: 
              Error("Seq(null): _*", "Expansion for non-repeated parameter") :: Nil =>
    }
  }

  def testDoesNotTakeTypeParameters {
    assertMatches(messages("def f = 0; f[Any]")) {
      case Error("[Any]", "f does not take type parameters") :: Nil =>
    }
  }

  def testMissingTypeParameter {
    assertMatches(messages("def f[A, B] = 0; f[Any]")) {
      case Error("[Any]", "Unspecified type parameters: B") :: Nil =>
    }
  }

  def testExcessTypeParameter {
    assertMatches(messages("def f[A] = 0; f[Any, Any]")) {
      case Error("Any", "Too many type arguments for f") :: Nil =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = new ApplicationAnnotator() {}
    val mock = new AnnotatorHolderMock

    val file = (Header + code).parse
    
    val seq = file.depthFirst.findByType(classOf[ScClass])
    Compatibility.seqClass = seq
    try {
      file.depthFirst.filterByType(classOf[ScReferenceElement]).foreach {
        annotator.annotateReference(_, mock)
      }

      file.depthFirst.filterByType(classOf[ScMethodCall]).foreach {
        annotator.annotateMethodInvocation(_, mock)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}