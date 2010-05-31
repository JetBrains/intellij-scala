package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScopeAnnotator, Message}
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
    assertMatches(messages("def f {}; f")) {
      case Nil =>
    }
    assertMatches(messages("def f() {}; f()")) {
      case Nil =>
    }
    assertMatches(messages("def f() {}; f")) {
      case Nil =>
    }
    assertMatches(messages("def f(p: Any) {}; f(null)")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: Any, b: Any) {}; f(null, null)")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: Any)(b: Any) {}; f(null)(null)")) {
      case Nil =>
    }
  }
 
  def testDoesNotTakeParameters {
    // TODO must not be applicable
//    assertMatches(messages("def f {}; f()")) {
//      case Error("()", DoesNotTakeParameters()) :: Nil =>
//    }
    assertMatches(messages("def f {}; f(null)")) {
      case Error("(null)", DoesNotTakeParameters()) :: Nil =>
    }
    assertMatches(messages("def f {}; f(null, null)")) {
      case Error("(null, null)", DoesNotTakeParameters()) :: Nil =>
    }    
    assertMatches(messages("def f {}; f(null)(null)")) {
      case Error("(null)", DoesNotTakeParameters()) :: Nil =>
    }
  }
  
  def testTooManyArguments {
    assertMatches(messages("def f() {}; f(null)")) {
      case Error("(null)", TooManyArguments()) :: Nil =>
    }
    assertMatches(messages("def f(p: Any) {}; f(null, null)")) {
      case Error("(null, null)", TooManyArguments()) :: Nil =>
    }
    assertMatches(messages("def f(a: Any, b: Any) {}; f(null, null, null)")) {
      case Error("(null, null, null)", TooManyArguments()) :: Nil =>
    }
    // TODO
//    assertMatches(messages("def f(a: Any)(b: Any) {}; f(null)(null)(null)")) {
//      case Error("(null)(null)(null)", TooManyArguments()) :: Nil =>
//    }
  }
  
  def testMissingArguments {
    assertMatches(messages("def f(p: Any) {}; f")) {
      case Error("f", MissingArguments()) :: Nil =>
    }
    assertMatches(messages("def f(a: Any, b: Any) {}; f")) {
      case Error("f", MissingArguments()) :: Nil =>
    }
    assertMatches(messages("def f(a: Any)(b: Any) {}; f")) {
      case Error("f", MissingArguments()) :: Nil =>
    }
  }

  def testNotEnoughArguments {
    assertMatches(messages("def f(p: Any) {}; f()")) {
      case Error("()", Inapplicable()) :: Nil =>
    }
    assertMatches(messages("def f(a: Any, b: Any) {}; f()")) {
      case Error("()", Inapplicable()) :: Nil =>
    }
    assertMatches(messages("def f(a: Any, b: Any) {}; f(null)")) {
      case Error("(null)", Inapplicable()) :: Nil =>
    }
    // TODO must not be applicable
//    assertMatches(messages("def f(a: Any)(b: Any) {}; f(null)")) {
//      case Error("(null)", Inapplicable()) :: Nil =>
//    }
  }
  

  //   def testInapplicable {
//     assertMatches(messages("def f(p: Any) {}; f()")) {
//       case Error("(null)", _) :: Nil =>
//     }
//
//     
//    assertMatches(messages("def f() {}; f(null)")) {
//      case Error("(null)", _) :: Nil =>
//    }
//  }
  
  def testMessages() {
    assertMatches(messages("def f {}; f(null)")) {
      case Error(_, "f does not take parameters") :: Nil =>
    }
    assertMatches(messages("def f(p: Any) {}; f(null, null)")) {
      case Error(_, "Too many arguments for method f(Any)") :: Nil =>
    }
    assertMatches(messages("def f(p: Any) {}; f")) {
      case Error("f", "Missing arguments for method f(Any)") :: Nil =>
    }
   //TODO test not enoght arguments message
  }
  // multiple
  // type parameters
  // too many args
  // not enough arguments
  // type mismatch
  // default
  // named
  // implicits
  // nfix
  // constructor 
  // inside block expression
 
  def messages(code: String): List[Message] = {
    val psi = code.parse
    val annotator = new ReferenceAnnotator() {}
    val mock = new AnnotatorHolderMock

    psi.depthFirst.filterByType(classOf[ScReferenceElement]).foreach {
      annotator.annotateReference(_, mock)  
    }
    
    mock.annotations
  }
  
  object Inapplicable {
    def unapply(message: String) = message.contains("Not applicable")
  }
  
  object DoesNotTakeParameters {
    def unapply(message: String) = message.contains("not take parameters")
  }
  
  object TooManyArguments {
    def unapply(message: String) = message.contains("Too many arguments")
  }
  
  object MissingArguments {
    def unapply(message: String) = message.contains("Missing arguments")
  }
}