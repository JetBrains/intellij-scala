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

  def testInheritanceFromNonFinalClass {
    assertMatches(messages("class C; new C {}")) {
      case Nil =>
    }
  }

  def testInheritanceFromFinalClass {
    assertMatches(messages("final class F; new F {}")) {
      case Error("F", InheritanceFromFinalClass("F")) :: Nil =>
    }

    assertMatches(messages("final class F; trait T; new F with T {}")) {
      case Error("F", InheritanceFromFinalClass("F")) :: Nil =>
    }
  }

  def testInheritanceFromFinalClassWithoutBody {
    assertMatches(messages("final class F; new F")) {
      case Nil =>
    }
  }

  def testInheritanceFromFinalClassWithTypeDefinition {
    assertMatches(messages("object O { final class F {}; class C extends F }")) {
      case Error("F", InheritanceFromFinalClass("F")) :: Nil =>
    }

    assertMatches(messages("object O { final class F {}; class C extends F {} }")) {
      case Error("F", InheritanceFromFinalClass("F")) :: Nil =>
    }
  }

  def testMultipleTraitInheritance {
    assertMatches(messages("trait T; new T with T {}")) {
      case Error("T", MultipleTraitInheritance("T")) ::
              Error("T", MultipleTraitInheritance("T")) :: Nil =>
    }

    assertMatches(messages("object O { trait T; class C extends T with T {} }")) {
      case Error("T", MultipleTraitInheritance("T")) ::
              Error("T", MultipleTraitInheritance("T")) :: Nil =>
    }

    assertMatches(messages("trait T; new T with T with T {}")) {
      case Error("T", MultipleTraitInheritance("T")) ::
              Error("T", MultipleTraitInheritance("T")) ::
              Error("T", MultipleTraitInheritance("T")) :: Nil =>
    }
  }

  def testNeedsToBeTrait {
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


  def testNeedsToBeTraitAndMultipleTraitInheritance {
    assertMatches(messages("class C; new C with C")) {
      case Error("C", NeedsToBeTrait("C")) :: Nil =>
    }
  }

  def testAbstractInstantiationWithTrait {
    assertMatches(messages("trait T; new T {}")) {
      case Nil =>
    }

    assertMatches(messages("trait T; new T")) {
      case Error("T", AbstractInstantiation("Trait T")) :: Nil =>
    }
  }

  def testAbstractInstantiationWithClass {
    assertMatches(messages("abstract class C; new C {}")) {
      case Nil =>
    }

    assertMatches(messages("abstract class C; new C")) {
      case Error("C", AbstractInstantiation("Class C")) :: Nil =>
    }
  }

  def testAbstractInstantiationAndMethod {
    assertMatches(messages("trait T { def f }; new T")) {
      case Error("T", AbstractInstantiation("Trait T")) :: Nil =>
    }
  }

  def testAbstractInstantiationWithConcreteClass {
    assertMatches(messages("class C; new C {}")) {
      case Nil =>
    }

    assertMatches(messages("class C; new C")) {
      case Nil =>
    }
  }

  def testAbstractInstantiationWithInstantiation {
    assertMatches(messages("abstract class C; trait T; new C with T")) {
      case Nil =>
    }
  }

  def testAbstractInstantiationTypeExtension {
    assertMatches(messages("object O { trait A; trait B extends A }")) {
      case Nil =>
    }
  }

  def testNeedsToBeAbstract {
    val Message = TemplateDefinitionAnnotator.needsToBeAbstract(
      "Class", "C", ("f: Unit", "O.T"))

    assertMatches(messages("object O { trait T { def f }; class C extends T {} }")) {
      case Error("C", Message) :: Nil =>
    }
  }

  def testNeedsToBeAbstractWithTrait {
    assertMatches(messages("object O { trait A { def f }; trait B extends A {} }")) {
      case Nil =>
    }
  }

  def testNeedsToBeAbstractWithAbstractClass {
    assertMatches(messages("object O { trait A { def f }; abstract class B extends A {} }")) {
      case Nil =>
    }
  }

  def testNeedsToBeAbstractMultipleMembers {
    val Message = TemplateDefinitionAnnotator.needsToBeAbstract(
      "Class", "C", ("a: Unit", "O.T"), ("b: Unit", "O.T"))

    assertMatches(messages("object O { trait T { def a; def b }; class C extends T {} }")) {
      case Error("C", Message) :: Nil =>
    }
  }

  def testNeedsToBeAbstractPlaceDiffer {
    val Message = TemplateDefinitionAnnotator.needsToBeAbstract(
      "Class", "C", ("a: Unit", "O.A"), ("b: Unit", "O.B"))

    assertMatches(messages("object O { trait A { def a }; trait B { def b }; class C extends A with B {} }")) {
      case Error("C", Message) :: Nil =>
    }
  }

  def testObjectCreationImpossible {
    val Message = TemplateDefinitionAnnotator.objectCreationImpossible(("f: Unit", "T"))

    assertMatches(messages("trait T { def f }; new T {}")) {
      case Error("T", Message) :: Nil =>
    }
  }

  def testObjectCreationImpossibleAndWith {
    val Message = TemplateDefinitionAnnotator.objectCreationImpossible(("f: Unit", "T"))

    assertMatches(messages("class C; trait T { def f }; new C with T {}")) {
      case Error("C", Message) :: Nil =>
    }
  }

  def testObjectCreationImpossibleWithoutBody {
    val Message = TemplateDefinitionAnnotator.objectCreationImpossible(("f: Unit", "T"))

    assertMatches(messages("class C; trait T { def f }; new C with T")) {
      case Error("C", Message) :: Nil =>
    }
  }

  private def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val definition = (Header + code).parse.depthFirst.toSeq.reverseIterator.findByType(classOf[ScTemplateDefinition]).get
    
    val annotator = new TemplateDefinitionAnnotator() {}
    val mock = new AnnotatorHolderMock
    
    annotator.annotateTemplateDefinition(definition, mock, true)
    mock.annotations
  }
  
  private val InheritanceFromFinalClass = "Illegal inheritance from final class (\\w+)".r

  private val MultipleTraitInheritance = "Trait (\\w+) inherited multiple times".r

  private val NeedsToBeTrait = "Class (\\w+) needs to be trait to be mixed in".r

  private val AbstractInstantiation = "(\\w+\\s\\w+) is abstract; cannot be instantiated".r
}