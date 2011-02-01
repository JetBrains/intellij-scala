package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
import lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * Pavel Fatin
 */

class TemplateDefinitionAnnotatorTest extends SimpleTestCase {
  final val Header = ""

  def testFine {
    assertMatches(messages("class C; new C {}")) {
      case Nil =>
    }
  }

  def testFinal {
    assertMatches(messages("final class F; new F {}")) {
      case Error("F", InheritanceFromFinalClass("F")) :: Nil =>
    }

    assertMatches(messages("final class F; trait T; new F with T {}")) {
      case Error("F", InheritanceFromFinalClass("F")) :: Nil =>
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
      case Error("T", NeedsToBeTrait("T")) :: Nil =>
    }
    assertMatches(messages("class C; class T1; class T2; new C with T1 with T2")) {
      case Error("T1", NeedsToBeTrait("T1")) ::
              Error("T2", NeedsToBeTrait("T2")) :: Nil =>
    }
  }

  //TODO Why do we need an enclosing object for F to be resolved?
  def testFinalWithTypeDefinition {
    assertMatches(messages("object O { final class F {}; class C extends F }")) {
      case Error("F", InheritanceFromFinalClass("F")) :: Nil =>
    }

    assertMatches(messages("object O { final class F {}; class C extends F {} }")) {
      case Error("F", InheritanceFromFinalClass("F")) :: Nil =>
    }
  }

  def testMultiInheritance {
    assertMatches(messages("trait T; new T with T {}")) {
      case Error("T", MultipleTraitInheritance("T")) ::
              Error("T", MultipleTraitInheritance("T")) :: Nil =>
    }

    assertMatches(messages("trait T; new T with T with T {}")) {
      case Error("T", MultipleTraitInheritance("T")) ::
              Error("T", MultipleTraitInheritance("T")) ::
              Error("T", MultipleTraitInheritance("T")) :: Nil =>
    }
  }

  def testMultiInheritanceWithMixinClass {
    assertMatches(messages("class C; new C with C")) {
      case Error("C", NeedsToBeTrait("C")) :: Nil =>
    }
  }

  def testTraitInstantiation {
    assertMatches(messages("trait T; new T {}")) {
      case Nil =>
    }

    assertMatches(messages("trait T; new T")) {
      case Error("T", AbstractInstantiation("Trait T")) :: Nil =>
    }
  }

  def testAbstractClassInstantiation {
    assertMatches(messages("abstract class C; new C {}")) {
      case Nil =>
    }

    assertMatches(messages("abstract class C; new C")) {
      case Error("C", AbstractInstantiation("Class C")) :: Nil =>
    }
  }

  def testConcreteClassInstantiation {
    assertMatches(messages("class C; new C {}")) {
      case Nil =>
    }

    assertMatches(messages("class C; new C")) {
      case Nil =>
    }
  }

  def testAbstractAndWithInstantiation {
    assertMatches(messages("abstract class C; trait T; new C with T")) {
      case Nil =>
    }
  }

  def testAbstractTypeExtension {
    assertMatches(messages("object O { trait A; trait B extends A }")) {
      case Nil =>
    }
  }

  private def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val definition = (Header + code).parse.depthFirst.toSeq.reverseIterator.findByType(classOf[ScTemplateDefinition]).get
    
    val annotator = new TemplateDefinitionAnnotator() {}
    val mock = new AnnotatorHolderMock
    
    annotator.annotateTemplateDefinition(definition, mock)
    mock.annotations
  }
  
  private val InheritanceFromFinalClass = "Illegal inheritance from final class (\\w+)".r

  private val MultipleTraitInheritance = "Trait (\\w+) inherited multiple times".r

  private val NeedsToBeTrait = "Class (\\w+) needs to be trait to be mixed in".r

  private val AbstractInstantiation = "(\\w+\\s\\w+) is abstract; cannot be instantiated".r
}