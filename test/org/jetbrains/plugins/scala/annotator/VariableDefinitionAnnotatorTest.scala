package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition

/**
 * Pavel.Fatin, 18.05.2010
 */

class VariableDefinitionAnnotatorTest extends SimpleTestCase {
  final val Header = "class A; class B; object A extends A; object B extends B\n"

  def testFine() {
    assertMatches(messages("var v = A")) {
      case Nil =>
    }
    assertMatches(messages("var v: A = A")) {
      case Nil =>
    }
    assertMatches(messages("var foo, bar = A")) {
      case Nil =>
    }
    assertMatches(messages("var foo, bar: A = A")) {
      case Nil =>
    }
  }

  def testTypeMismatch() {
    assertMatches(messages("var v: A = B")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeMismatchMessage() {
    assertMatches(messages("var v: A = B")) {
      case Error(_, "Type mismatch, found: B.type, required: A") :: Nil =>
    }
  }

  def testTypeMismatchWithMultiplePatterns() {
    assertMatches(messages("var foo, bar: A = B")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  //todo: requires Function1 trait in scope
  /*def testImplicitConversion {
    assertMatches(messages("implicit def toA(b: B) = A; var v: A = B")) {
      case Nil =>
    }
  }*/

  def testWildchar() {
    assertMatches(messages("var v: A = _")) {
      case Nil =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val file: ScalaFile = (Header + code).parse
    val definition = file.depthFirst.findByType(classOf[ScVariableDefinition]).get
    
    val annotator = new VariableDefinitionAnnotator() {}
    val mock = new AnnotatorHolderMock(file)
    annotator.annotateVariableDefinition(definition, mock, highlightErrors = true)
    mock.annotations
  }
  
  val TypeMismatch = ContainsPattern("Type mismatch")
}