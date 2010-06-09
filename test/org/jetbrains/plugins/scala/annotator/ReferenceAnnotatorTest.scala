package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import lang.psi.api.base.ScReferenceElement

/**
 * Pavel.Fatin, 18.05.2010
 */

class ReferenceAnnotatorTest extends SimpleTestCase {
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
 
  def testDoesNotTakeParameters {
    assertMatches(messages("def f {}; f(Unit, null)")) {
      case Error("(Unit, null)", "f does not take parameters") :: Nil =>
    }    
  }
  
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
  
  def messages(code: String): List[Message] = {
    val psi = code.parse
    val annotator = new ReferenceAnnotator() {}
    val mock = new AnnotatorHolderMock

    psi.depthFirst.filterByType(classOf[ScReferenceElement]).foreach {
      annotator.annotateReference(_, mock)  
    }
    
    mock.annotations
  }
}