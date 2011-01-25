package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
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

    assertMatches(messages("final class F; trait T; new F with T {}")) {
      case Error("F", InheritanceFromFinalClass()) :: Nil =>
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

  def testMixingClass {
    assertMatches(messages("class C; trait T; new C with T")) {
      case Nil =>
    }
    assertMatches(messages("class C; trait T1; trait T2; new C with T1 with T2")) {
      case Nil =>
    }
    assertMatches(messages("class C; class T; new C with T")) {
      case Error("T", "Class T needs to be trait to be mixed in") :: Nil =>
    }
    assertMatches(messages("class C; class T1; class T2; new C with T1 with T2")) {
      case Error("T1", "Class T1 needs to be trait to be mixed in") ::
              Error("T2", "Class T2 needs to be trait to be mixed in") :: Nil =>
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
//  trait T2
//  class CC
//  class C extends T with CC// trait T is inherited twice

//  error: class CC needs to be a trait to be mixed in
//class C extends T with CC// trait T is inherited twice
}