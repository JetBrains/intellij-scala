package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._
import lang.psi.api.toplevel.typedef.ScTrait

/**
 * Pavel.Fatin, 18.05.2010
 */

class RepeatedTest extends Base {
//  override def setUp {
//    super.setUp()
//    Compatibility.mockSeqClass("trait Seq[+A]".parse(classOf[ScTrait]))
//  }
  
  def testMalformedDefinition {
    assertMatches(problems("def f(a: A*, b: B) {}; f(A, B)")) {
      case MalformedDefinition() :: Nil =>
    }
    assertMatches(problems("def f(a: A*, b: B) {}; f(A)")) {
      case MalformedDefinition() :: Nil =>
    }
    assertMatches(problems("def f(a: A*, b: B*) {}; f(A, B)")) {
      case MalformedDefinition() :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B*, c: C) {}; f(A, B, C)")) {
      case MalformedDefinition() :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B*, c: C*) {}; f(A, B, C)")) {
      case MalformedDefinition() :: Nil =>
    }
  }
  
  def testMalformedDefinitionClauses {
    assertMatches(problems("def f(a: A)(b: B*, c: C) {}; f(A)(B, C)")) {
      case MalformedDefinition() :: Nil =>
    }
  }
  
  def testValidDefinition {
    assertMatches(problems("def f(a: A*)(b: B) {}; f(A)(B)")) {
      case Nil =>
    }
  }
  
  def testFineSingle {
    assertMatches(problems("def f(a: A*) {}; f()")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A*) {}; f(A)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A*) {}; f(A, A)")) {
      case Nil =>
    }
  }
  
  def testFineSecond {
    assertMatches(problems("def f(a: A, b: B*) {}; f(A)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A, b: B*) {}; f(A, B)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A, b: B*) {}; f(A, B, B)")) {
      case Nil =>
    }
  }
  
  def testFineSecondSameType {
    assertMatches(problems("def f(a: A, b: A*) {}; f(A)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A, b: A*) {}; f(A, A)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A, b: A*) {}; f(A, A, A)")) {
      case Nil =>
    }
  }
  
  def testFineThird {
    assertMatches(problems("def f(a: A, b: B, c: C*) {}; f(A, B)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A, b: B, c: C*) {}; f(A, B, C)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A, b: B, c: C*) {}; f(A, B, C, C)")) {
      case Nil =>
    }
  }
  
  def testMissedArguments {
    assertMatches(problems("def f(a: A*) {}; f")) {
      case MissedParametersClause(_) :: Nil =>
    }
  }
  
  def testTypeMismatch {
    assertMatches(problems("def f(a: A*) {}; f(B)")) {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
//    assertMatches(problems("def f(a: A*) {}; f(B, B)")) {
//      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("B"), Type("A")) :: Nil =>
//    }
//    assertMatches(problems("def f(a: A*) {}; f(A, B)")) {
//      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
//    }
//    assertMatches(problems("def f(a: A*) {}; f(B, A)")) {
//      case Nil =>
//    }
//    assertMatches(problems("def f(a: A*) {}; f(A, B, A)")) {
//      case Nil =>
//    }
//    assertMatches(problems("def f(a: A*) {}; f(A, B, B, A)")) {
//      case Nil =>
//    }
  }
}