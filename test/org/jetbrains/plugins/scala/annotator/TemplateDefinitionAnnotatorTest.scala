package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
import lang.psi.api.expr.ScNewTemplateDefinition

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
      case Error("F", "Illegal inheritance from final class F") :: Nil =>
    }

    assertMatches(messages("class C; final class F; new C with F {}")) {
      case Error("F", "Illegal inheritance from final class F") :: Nil =>
    }
  }

  def testFinalWithoutBody {
    assertMatches(messages("final class F; new F")) {
      case Nil =>
    }
  }

  def messages(@Language("Scala") code: String): List[Message] = {
    val definition = (Header + code).parse.depthFirst.findByType(classOf[ScNewTemplateDefinition]).get
    
    val annotator = new TemplateDefinitionAnnotator() {}
    val mock = new AnnotatorHolderMock
    
    annotator.annotateTemplateDefinition(definition, mock)
    mock.annotations
  }
  
  val TypeMismatch = containsPattern("Type mismatch")

  def containsPattern(fragment: String) = new {
    def unapply(s: String) = s.contains(fragment)
  }
}

//final class C
//trait T extends C

//object holder {
//  final class F;
//  new F {}
//}