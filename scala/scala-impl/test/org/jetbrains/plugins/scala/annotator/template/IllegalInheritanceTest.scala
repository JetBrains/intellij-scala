package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
class IllegalInheritanceTest extends AnnotatorTestBase[ScTemplateDefinition] {

  def testFine(): Unit = {
    assertNothing(messages("class C"))
    assertNothing(messages("trait X; class C { self: X => }"))
    assertNothing(messages("trait T; class C extends T"))
    assertNothing(messages("trait X; trait T { self: X => }; class C extends X with T"))
    assertNothing(messages("trait X; trait T { self: X => }; class C extends T with X"))
    assertNothing(messages("trait X; trait T { self: X => }; class C extends T { self: X => }"))
    assertNothing(messages("trait X; trait Y extends X; trait T { self: X => }; class C extends T { self: Y => }"))
    assertNothing(messages("trait U; trait X[A]; trait Y[A] { self: X[A] => }; class Z extends X[U]; " +
      "object A {new Z with Y[U]}"))
    assertNothing(messages(
      """
        |object C {
        |  trait A {self: X => };
        |  trait X extends A
        |}
      """.stripMargin
    ))
    assertNothing(messages(
      """
        |object S {
        |  trait A
        |  trait B {
        |    this : A =>
        |  }
        |  trait C
        |  class D extends A with B {
        |    this: C =>
        |  }
        |}
      """.stripMargin
    ))
  }

  def testIllegalInheritance(): Unit = {
    val firstMessage = ScalaBundle.message("illegal.inheritance.self.type", "C", "X")
    assertMatches(messages("trait X; trait T { self: X => }; class C extends T")) {
      case Error("T", `firstMessage`) :: Nil =>
    }

    val secondMessage = ScalaBundle.message("illegal.inheritance.self.type", "C", "X")
    assertMatches(messages("trait X; trait T { self: X => }; class C extends Object with T")) {
      case Error("T", `secondMessage`) :: Nil =>
    }

    val thirdMessage = ScalaBundle.message("illegal.inheritance.self.type", "C with Y", "X")
    assertMatches(messages("trait X; trait Y; trait T { self: X => }; class C extends T { self: Y => }")) {
      case Error("T", `thirdMessage`) :: Nil =>
    }

    val fourthMessage = ScalaBundle.message("illegal.inheritance.self.type", "C with X", "Y")
    assertMatches(messages("trait X; trait Y extends X; trait T { self: Y => }; class C extends T { self: X => }")) {
      case Error("T", `fourthMessage`) :: Nil =>
    }
  }

  def testCyclicSelfTypeSubstitutor(): Unit = {
    val code =
      """
        |trait A {
        |
        |  trait B {
        |    self: C =>
        |  }
        |
        |  trait C {
        |    self: B =>
        |
        |    def foo(s: B) = s
        |
        |    foo(a)
        |  }
        |
        |  def foo(a: A): A = a
        |
        |  def a: A = new A {}
        |}
      """.stripMargin

    //todo: the code above doesn't compile, so we probably should have an error message
    //but at least we don't have an infinite recursion here (see SCL-13410)
    assertNothing(messages(code))
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateIllegalInheritance(element)
}