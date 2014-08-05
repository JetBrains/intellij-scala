package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
import lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel.Fatin, 18.05.2010
 */

class PatternDefinitionAnnotatorTest extends SimpleTestCase {
  final val Header = "class A; class B; object A extends A; object B extends B\n"

  def testFine {
    assertMatches(messages("val v = A")) {
      case Nil =>
    }
    assertMatches(messages("val v: A = A")) {
      case Nil =>
    }
    assertMatches(messages("val foo, bar = A")) {
      case Nil =>
    }
    assertMatches(messages("val foo, bar: A = A")) {
      case Nil =>
    }
  }

  def testTypeMismatch {
    assertMatches(messages("val v: A = B")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeMismatchMessage {
    assertMatches(messages("val v: A = B")) {
      case Error(_, "Type mismatch, found: B.type, required: A") :: Nil =>
    }
  }

  def testTypeMismatchWithMultiplePatterns {
    assertMatches(messages("val foo, bar: A = B")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  //todo: requires Function1 trait in scope
  /*def testImplicitConversion {
    assertMatches(messages("implicit def toA(b: B) = A; val v: A = B")) {
      case Nil =>
    }
  }*/

  def testWildchar {
    assertMatches(messages("val v: A = _")) {
      case Nil =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val definition = (Header + code).parse.depthFirst.findByType(classOf[ScPatternDefinition]).get
    
    val annotator = new PatternDefinitionAnnotator() {}
    val mock = new AnnotatorHolderMock
    
    annotator.annotatePatternDefinition(definition, mock, true)
    mock.annotations
  }
  
  val TypeMismatch = containsPattern("Type mismatch")

  def containsPattern(fragment: String) = new {
    def unapply(s: String) = s.contains(fragment)
  }
}