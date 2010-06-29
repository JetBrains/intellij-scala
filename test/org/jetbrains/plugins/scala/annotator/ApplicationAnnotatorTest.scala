package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import lang.psi.api.base.ScReferenceElement
import lang.psi.types.Compatibility
import lang.psi.api.toplevel.typedef.ScClass

/**
 * Pavel.Fatin, 18.05.2010
 */

class ApplicationAnnotatorTest extends SimpleTestCase {
  val Header = """
  class Seq[+A] 
  object Seq { def apply[A](a: A) = new Seq[A] } 
  class A; class B; 
  object A; object B
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
      case Error("f", "Missing arguments for method f(Any, Any)") :: Nil =>
    }
  }
  
  def testExcessArguments {
    assertMatches(messages("def f() {}; f(null, Unit)")) {
      case Error("null", "Too many arguments for method f") ::
              Error("Unit", "Too many arguments for method f") :: Nil =>
    }
  }

  def testMissedParameters {
    assertMatches(messages("def f(a: Any, b: Any) {}; f()")) {
      case Error("()", "Unspecified value parameters: a: Any, b: Any") :: Nil =>
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
      case Error("B", "Type mismatch, expected: A, actual: B") :: 
              Error("A", "Type mismatch, expected: B, actual: A") :: Nil =>
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
  
  def messages(code: String): List[Message] = {
    val annotator = new ApplicationAnnotator() {}
    val mock = new AnnotatorHolderMock

    val file = (Header + code).parse
    
    val seq = file.depthFirst.findByType(classOf[ScClass])
    Compatibility.mockSeqClass(seq.get)
    
    file.depthFirst.filterByType(classOf[ScReferenceElement]).foreach {
      annotator.annotateReference(_, mock)  
    }
    
    mock.annotations
  }
}