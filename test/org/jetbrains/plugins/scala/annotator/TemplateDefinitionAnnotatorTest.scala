package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
import lang.psi.api.expr.ScNewTemplateDefinition
import lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * Pavel Fatin
 */

class TemplateDefinitionAnnotatorTest extends SimpleTestCase {
  val Header = ""

  def testFine {
    assertMatches(messages("class C; new C {}")) {
      case Nil =>
    }
  }

  def testFinal {
    assertMatches(messages("final class F; new F {}")) {
      case Error("F", InheritanceFromFinalClass()) :: Nil =>
    }

    assertMatches(messages("class C; final class F; new C with F {}")) {
      case Error("F", InheritanceFromFinalClass()) :: Nil =>
    }
  }

  def testFinalMultiple {
    assertMatches(messages("final class A; final class B; final class C; new A with B with C {}")) {
      case Error("A", _) :: Error("B", _) :: Error("C", _) :: Nil =>
    }
  }

  def testFinalMessage {
    assertMatches(messages("final class F; new F {}")) {
      case Error(_, "Illegal inheritance from final class F") :: Nil =>
    }
  }

  def testFinalWithoutBody {
    assertMatches(messages("final class F; new F")) {
      case Nil =>
    }
  }

  //TODO Why do we need an enclosing object for F to be resolved?
  def testFinalWithTypeDefinition {
    assertMatches(messages("object O { final class F {}; class C extends F }")) {
      case Error("F", InheritanceFromFinalClass()) :: Nil =>
    }

    assertMatches(messages("object O { final class F {}; class C extends F {} }")) {
      case Error("F", InheritanceFromFinalClass()) :: Nil =>
    }
  }

  private def messages(@Language("Scala") code: String): List[Message] = {
    val definition = (Header + code).parse.depthFirst.toSeq.reverseIterator.findByType(classOf[ScTemplateDefinition]).get
    
    val annotator = new TemplateDefinitionAnnotator() {}
    val mock = new AnnotatorHolderMock
    
    annotator.annotateTemplateDefinition(definition, mock)
    mock.annotations
  }
  
  private val InheritanceFromFinalClass = containsPattern("Illegal inheritance from final class")

  private def containsPattern(fragment: String) = new {
    def unapply(s: String) = s.contains(fragment)
  }

//  trait T
//  class C extends T with T // trait T is inherited twice

}