package org.jetbrains.plugins.scala.annotator.template

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator._
import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, Message, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class ObjectCreationImpossibleTest extends AnnotatorTestBase[ScTemplateDefinition] {
  import Message._

  def testFineNew(): Unit = {
    assertNothing(messages("class C; new C"))
    assertNothing(messages("class C; new C {}"))
    assertNothing(messages("class C; trait T; new C with T"))
    assertNothing(messages("class C; trait T; new C with T {}"))
  }

  def testFineObject(): Unit = {
    assertNothing(messages("class C; object O extends C"))
    assertNothing(messages("class C; object O extends C {}"))
    assertNothing(messages("class C; trait T; object O extends C with T"))
    assertNothing(messages("class C; trait T; object O extends C with T {}"))
  }

  def testTypeSkipDeclarations(): Unit = {
    assertNothing(messages("class C { def f }"))
  }

  def testSkipAbstractInstantiations(): Unit = {
    assertNothing(messages("trait T; new T"))
  }

  def testSkipConcrete(): Unit = {
    assertNothing(messages("class C { def f }; new C"))
    assertNothing(messages("class C { def f }; new C {}"))
    assertNothing(messages("class C { def f }; new Object with C"))
    assertNothing(messages("class C { def f }; new Object with C {}"))
  }

  def testSkipInvalidDirect(): Unit = {
    assertNothing(messages("new { def f }"))
    assertNothing(messages("new Object { def f }"))
    assertNothing(messages("object O { def f }"))
  }

  def testUndefinedMember(): Unit = {
    val Message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; new T {}")) {
      case Error("T", Message) :: Nil =>
    }
  }

  def testUndefinedMemberObject(): Unit = {
    val Message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; object O extends T {}")) {
      case Error("object O extends T", Message) :: Nil =>
    }
  }

  def testUndefinedAndWith(): Unit = {
    val Message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; new Object with T {}")) {
      case Error("Object", Message) :: Nil =>
    }
  }

  def testNeedsToBeAbstractPlaceDiffer(): Unit = {
    val Message = objectCreationImpossibleMessage(
      ("b: Unit", "Holder.B"), ("a: Unit", "Holder.A"))
    val ReversedMessage = objectCreationImpossibleMessage(
      ("a: Unit", "Holder.A"), ("b: Unit", "Holder.B"))

    assertMatches(messages("trait A { def a }; trait B { def b }; new A with B {}")) {
      case Error("A", Message) :: Nil =>
      case Error("A", ReversedMessage) :: Nil =>
    }
  }

  def testSkipTypeDeclarationSCL2887(): Unit = {
    assertNothing(messages("trait A { type a }; new A {}"))
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateObjectCreationImpossible(element)
}

class EnumCaseCreationImpossibleTest extends AnnotatorTestBase[ScTemplateDefinition] {
  import Message._

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3

  override protected def messages(@Language(value = "Scala 3", prefix = Prefix, suffix = Suffix) code: String): Option[List[Message]] =
    super.messages(code)

  def testEnumCaseWithoutExplicitExtends(): Unit = {
    val message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; enum E extends T { case C }")) {
      case Error("C", `message`) :: Nil =>
    }
  }

  def testEnumCasesInOneLine(): Unit = {
    val message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; enum E extends T { case C, D }")) {
      case Error("C", `message`) :: Error("D", `message`) :: Nil =>
      case Error("D", `message`) :: Error("C", `message`) :: Nil =>
    }
  }

  def testEnumCaseWithExplicitExtends(): Unit = {
    val message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; enum E extends T { case C extends E }")) {
      case Error("C extends E", `message`) :: Nil =>
    }
  }

  def testEnumCaseWithTypeDeclarationInsideTrait(): Unit = {
    val message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { type T1; def f }; enum E extends T { case C; case D(i: Int) }")) {
      case Error("C", `message`) :: Error("D(i: Int)", `message`) :: Nil =>
      case Error("D(i: Int)", `message`) :: Error("C", `message`) :: Nil =>
    }
  }

  def testEnumCaseWithExplicitExtendsAndMoreParents(): Unit = {
    val message1 = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))
    val message2 = objectCreationImpossibleMessage(("g: Unit", "Holder.R"))

    assertMatches(messages("trait T { def f }; trait R { def g }; enum E extends T { case C extends E with R }")) {
      case Error("C extends E with R", `message1`) :: Error("C extends E with R", `message2`) :: Nil =>
      case Error("C extends E with R", `message2`) :: Error("C extends E with R", `message1`) :: Nil =>
    }
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateEnumCaseCreationImpossible(element)
}
