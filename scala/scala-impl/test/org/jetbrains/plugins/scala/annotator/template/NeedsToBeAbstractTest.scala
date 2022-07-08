package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

abstract class NeedsToBeAbstractTestBase extends AnnotatorTestBase[ScTemplateDefinition] {

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateNeedsToBeAbstract(element)

  protected def message(params: String*) =
    ScalaBundle.message("member.implementation.required", params: _*)

}

class NeedsToBeAbstractTest extends NeedsToBeAbstractTestBase {

  def testFine(): Unit = {
    assertNothing(messages("class C"))
    assertNothing(messages("class C {}"))
    assertNothing(messages("trait T"))
    assertNothing(messages("trait T {}"))
    assertNothing(messages("abstract class C"))
    assertNothing(messages("abstract class C {}"))
    assertNothing(messages("abstract class C { def f }"))
    assertNothing(messages("trait T { def f }"))
  }

  def testSkipNew(): Unit = {
    assertNothing(messages("trait T { def f }; new Object with T"))
  }

  def testSkipObject(): Unit = {
    assertNothing(messages("trait T { def f }; object O extends T"))
  }

  def testUndefinedMember(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.C")

    assertMatches(messages("class C { def f }")) {
      case Error("class C", `message`) :: Nil =>
    }
  }

  def testUndefinedInheritedMember(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.T")

    assertMatches(messages("trait T { def f }; class C extends T")) {
      case Error("class C extends T", `message`) :: Nil =>
    }

    assertMatches(messages("trait T { def f }; class C extends T {}")) {
      case Error("class C extends T", `message`) :: Nil =>
    }
  }

  def testNeedsToBeAbstractPlaceDiffer(): Unit = {
    val message = this.message("Class", "C", "b: Unit", "Holder.B")
    val reversedMessage = this.message("Class", "C", "a: Unit", "Holder.A")

    assertMatches(messages("trait A { def a }; trait B { def b }; class C extends A with B {}")) {
      case Error("class C extends A with B", `message`) :: Nil =>
      case Error("class C extends A with B", `reversedMessage`) :: Nil =>
    }
  }

  def testClassWithConstructor(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.T")

    assertMatches(messages("trait T { def f }; class C(i: Int) extends T {}")) {
      case Error("class C(i: Int) extends T", `message`) :: Nil =>
    }
  }

  def testClassWithMultipleConstructorClauses(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.T")

    assertMatches(messages("trait T { def f }; class C(i: Int)(d: Double)(implicit b: Boolean) extends T {}")) {
      case Error("class C(i: Int)(d: Double)(implicit b: Boolean) extends T", `message`) :: Nil =>
    }
  }

  def testObjectOverrideDef(): Unit = {
    assertMatches(messages("trait A { def a }; class D extends A { object a };")) {
      case Nil =>
    }
  }

  def testClassWithScaladoc(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.T")
    val code =
      s"""
         |trait T { def f }
         |
         |/**
         | * Test
         | */
         |class C extends T
         |""".stripMargin

    assertMatches(messages(code)) {
      case Error("class C extends T", `message`) :: Nil =>
    }
  }
}

class NeedsToBeAbstractTest_WithScalaSdk extends NeedsToBeAbstractTestBase with ScalaSdkOwner {
  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(ScalaSDKLoader())

  override def setUp(): Unit = {
    super.setUp()
    setUpLibraries(fixture.getModule)
  }

  override def tearDown(): Unit = {
    disposeLibraries(fixture.getModule)
    super.tearDown()
  }

  def testClassWithScaladocAnnotationAnotherCommentAndModifiers(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.T")
    val code =
      s"""
         |trait T { def f }
         |
         |/**
         | * Test
         | */
         |@deprecated
         |// another comment
         |private class C extends T
         |""".stripMargin

    assertMatches(messages(code)) {
      case Error("private class C extends T", `message`) :: Nil =>
    }
  }
}

class NeedsToBeAbstractTest_Scala3 extends NeedsToBeAbstractTestBase {
  def testClassWithMultipleParents(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.T")

    assertMatches(messages("trait T { def f }; trait R; class C extends T, R {}", Scala3Language.INSTANCE)) {
      case Error("class C extends T, R", `message`) :: Nil =>
    }
  }

  def testClassWithDerivesClause(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.T")

    assertMatches(messages("trait T { def f }; trait R[A]; object R { def derived[A]: R[A] = null }; class C extends T derives R {}", Scala3Language.INSTANCE)) {
      case Error("class C extends T derives R", `message`) :: Nil =>
    }
  }

  def testGivenDefinition(): Unit = {
    val message = this.message("Class", "given_T", "f: Unit", "Holder.T")

    assertMatches(messages("trait T { def f }; given T with {}", Scala3Language.INSTANCE)) {
      case Error("given T with", `message`) :: Nil =>
    }
  }

  def testEnum(): Unit = {
    val message = this.message("Class", "E", "f: Unit", "Holder.T")

    assertMatches(messages("trait T { def f }; enum E extends T {}", Scala3Language.INSTANCE)) {
      case Error("enum E extends T", `message`) :: Nil =>
    }
  }
}
