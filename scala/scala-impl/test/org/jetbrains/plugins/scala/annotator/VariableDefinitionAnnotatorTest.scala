package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScVariableDefinitionAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.junit.experimental.categories.Category

/**
 * Pavel.Fatin, 18.05.2010
 */
@Category(Array(classOf[TypecheckerTests]))
class VariableDefinitionAnnotatorTest extends SimpleTestCase {
  final val Header = "class A; class B; object A extends A; object B extends B\n"

  def testFine(): Unit = {
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

  def testTypeMismatch(): Unit = {
    assertMatches(messages("var v: A = B")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeMismatchMessage(): Unit = {
    assertMatches(messages("var v: A = B")) {
      case Error(_, "Type mismatch, found: B.type, required: A") :: Nil =>
    }
  }

  def testTypeMismatchWithMultiplePatterns(): Unit = {
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

  def testWildchar(): Unit = {
    assertMatches(messages("var v: A = _")) {
      case Nil =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val file: ScalaFile = (Header + code).parse
    val definition = file.depthFirst().findByType[ScVariableDefinition].get

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)
    ScVariableDefinitionAnnotator.annotate(definition)
    mock.annotations
  }
  
  val TypeMismatch = ContainsPattern("Type mismatch")
}