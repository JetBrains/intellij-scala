package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel.Fatin, 18.05.2010
 */

class ParametersAnnotatorTest extends SimpleTestCase {
  final val Header = "class A; class B; class C;\n"
  
  def testFine {
    assertMatches(messages("def f(a: A) {}")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: A*) {}")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: A, b: B) {}")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: A, b: B*) {}")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: A, b: B, c: C*) {}")) {
      case Nil =>
    }
  }
  
  def testMalformed {
    assertMatches(messages("def f(a: A*, b: B) {}")) {
      case Error("a: A*", "*-parameter must come last") :: Nil =>
    }
    assertMatches(messages("def f(a: A, b: B*, c: C) {}")) {
      case Error("b: B*", "*-parameter must come last") :: Nil =>
    }
    assertMatches(messages("def f(a: A*, b: B*) {}")) {
      case Error("a: A*", "*-parameter must come last") :: Nil =>
    }
    assertMatches(messages("def f(a: A*, b: B*, c: C) {}")) {
      case Error("a: A*", "*-parameter must come last") :: 
              Error("b: B*", "*-parameter must come last") :: Nil =>
    }
    assertMatches(messages("def f(a: A*, c: C)(b: B*, c: C) {}")) {
      case Error("a: A*", "*-parameter must come last") :: 
              Error("b: B*", "*-parameter must come last") :: Nil =>
    }
  }
   
  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = new ParametersAnnotator() {}
    val mock = new AnnotatorHolderMock

    val function = (Header + code).parse.depthFirst.findByType(classOf[ScFunctionDefinition]).get
    
    annotator.annotateParameters(function.paramClauses, mock)
    mock.annotations
  }
}