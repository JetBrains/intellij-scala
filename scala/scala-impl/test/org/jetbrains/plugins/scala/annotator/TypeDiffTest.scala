package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure
import org.junit.Assert

class TypeDiffTest extends SimpleTestCase {
  // TODO test region nesting (for code folding)
  // TODO test separation of matched elements, such as [ or ]

  /* TODO:
    parameterized types
      function types
      infix types
    compound types
    structural types
    literal types
    existential types
    */

  def testSingular(): Unit = {
    assert(
      "class Foo",
      "Foo", "Foo"
    )
    assert(
      "class Foo; class Bar",
      "~Foo~", "~Bar~"
    )
  }

  def testParameterized(): Unit = {
    // Base
    assert(
      "class A; class Foo[T]; class Bar[T]",
      "Foo[A]", "Foo[A]"
    )
    assert(
      "class A; class Foo[T]; class Bar[T] extends Foo[T]",
      "Foo[A]", "Bar[A]"
    )
    assert(
      "class A; class Foo[T]; class Bar[T]",
      "~Foo~[A]", "~Bar~[A]"
    )

    // Arguments
    assert(
      "class A; class B extends A; class Foo[T]",
      "Foo[A]", "Foo[B]"
    )
    assert(
      "class A; class B; class Foo[T]",
      "Foo[~A~]", "Foo[~B~]"
    )
    assert(
      "class A; class B; class Foo[T1, T2]",
      "Foo[A, ~A~]", "Foo[A, ~B~]"
    )

    // Type and arguments
    assert(
      "class A; class Foo[T]; class Bar[T]",
      "~Foo~[A]", "~Bar~[A]"
    )
    assert(
      "class A; class B; class Foo[T]; class Bar[T]",
      "~Foo~[~A~]", "~Bar~[~B~]"
    )

    // Nesting
    assert(
      "class A; class B; class Foo[T]",
      "Foo[Foo[~A~]]", "Foo[Foo[~B~]]"
    )
  }

  private def assert(context: String, expectedDiff1: String, expectedDiff2: String): Unit = {
    val (tpe1, tpe2) = typesIn(context, clean(expectedDiff1), clean(expectedDiff2))
    val (actualDiff1, actualDiff2) = TypeDiff.forBoth(tpe1, tpe2)
    Assert.assertEquals("Incorrect diff (1)", expectedDiff1, asString(actualDiff1))
    Assert.assertEquals("Incorrect diff (2)", expectedDiff2, asString(actualDiff2))
    // Also make sure that the diff matches the standard presentation
    Assert.assertEquals("Incorrect presentation (1)", tpe1.presentableText, clean(asString(actualDiff1)))
    Assert.assertEquals("Incorrect presentation (2)", tpe2.presentableText, clean(asString(actualDiff2)))
  }

  private def clean(diff: String) = diff.replaceAll("~", "")

  private def asString(diff: TypeDiff) = diff.format((s, matches) => if (matches) s else s"~$s~")

  private def typesIn(context: String, type1: String, type2: String): (ScType, ScType) = {
    val Seq(tpe1, tpe2) = s"$context; null: $type1; null: $type2".parse.children.instancesOf[ScTypedExpression].toSeq.map(typeOf)
    (tpe1, tpe2)
  }

  private def typeOf(e: ScTypedExpression) = {
    val typeElement = e.typeElement.getOrElse(throw new IllegalArgumentException("No type element: " + e.getText))
    typeElement.`type`() match {
      case Failure(cause) => throw new IllegalArgumentException(typeElement.getText + ": " + cause)
      case Right(tpe) => tpe
    }
  }
}
