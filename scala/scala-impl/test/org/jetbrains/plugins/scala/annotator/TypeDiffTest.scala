package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.TypeDiff.{Match, Mismatch}
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure
import org.junit.Assert._

// TODO Work in progress
class TypeDiffTest extends SimpleTestCase {
  // TODO test region nesting (for code folding)
  // TODO test separation of matched elements, such as [ or ]

  /* TODO:
    parameterized types
      function types
      infix types
    tuple types
    compound types
    structural types
    literal types
    existential types
    java array types
    */

  def testSingular(): Unit = {
    assertDiffsAre(
      "class Foo",
      "Foo", "Foo"
    )
    assertDiffsAre(
      "class Foo; class Bar",
      "~Foo~", "~Bar~"
    )
  }

  def testParameterized(): Unit = {
    // Base type
    assertDiffsAre(
      "class A; class Foo[T]",
      "Foo[A]", "Foo[A]"
    )
    assertDiffsAre(
      "class A; class Foo[T]; class Bar[T]",
      "~Foo~[A]", "~Bar~[A]"
    )

    // Base type conformance
    assertDiffsAre(
      "class A; class Foo[T]; class Bar[T] extends Foo[T]",
      "Foo[A]", "Bar[A]"
    )

    // Arguments
    assertDiffsAre(
      "class A; class B; class Foo[T]",
      "Foo[~A~]", "Foo[~B~]"
    )
    assertDiffsAre(
      "class A; class B; class Foo[T1, T2]",
      "Foo[A, ~A~]", "Foo[A, ~B~]"
    )

    // Invariance
    assertDiffsAre(
      "class A; class B extends A; class Foo[T]",
      "Foo[~A~]", "Foo[~B~]"
    )
    assertDiffsAre(
      "class A; class B extends A; class Foo[T]",
      "Foo[~B~]", "Foo[~A~]"
    )

    // Covariance
    assertDiffsAre(
      "class A; class B extends A; class Foo[+T]",
      "Foo[A]", "Foo[B]"
    )
    assertDiffsAre(
      "class A; class B extends A; class Foo[+T]",
      "Foo[~B~]", "Foo[~A~]"
    )

    // Contravariance
    assertDiffsAre(
      "class A; class B extends A; class Foo[-T]",
      "Foo[~A~]", "Foo[~B~]"
    )
    assertDiffsAre(
      "class A; class B extends A; class Foo[-T]",
      "Foo[B]", "Foo[A]"
    )

    // Argument count
    assertDiffsAre(
      "class A; class B; class Foo[T]",
      "Foo[~A~]", "Foo[~A, B~]"
    )
    assertDiffsAre(
      "class A; class B; class Foo[T]",
      "Foo[~A, B~]", "Foo[~A~]" // TODO parse nested types? (create matching placeholders?)
    )

    // Base type and arguments
    assertDiffsAre(
      "class A; class Foo[T]; class Bar[T]",
      "~Foo~[A]", "~Bar~[A]"
    )
    assertDiffsAre(
      "class A; class B; class Foo[T]; class Bar[T]",
      "~Foo~[~A~]", "~Bar~[~B~]"
    )

    // Nesting
    assertDiffsAre(
      "class A; class B; class Foo[T]",
      "Foo[Foo[~A~]]", "Foo[Foo[~B~]]"
    )

    // Non-parameterized type
    assertDiffsAre(
      "class A; class B; class Foo[T]",
      "~Foo[A]~", "~B~"
    )
    assertDiffsAre(
      "class A; class B; class Foo[T]",
      "~A~", "~Foo[B]~"
    )
  }

  private def assertDiffsAre(context: String, expectedDiff1: String, expectedDiff2: String): Unit = {
    val (tpe1, tpe2) = typesIn(context, clean(expectedDiff1), clean(expectedDiff2))
    val (actualDiff1, actualDiff2) = TypeDiff.forBoth(tpe1, tpe2)
    val actualDiffString1 = asString(actualDiff1)
    val actualDiffString2 = asString(actualDiff2)
    assertEquals("Incorrect diff (1)", expectedDiff1, actualDiffString1)
    assertEquals("Incorrect diff (2)", expectedDiff2, actualDiffString2)

    // Also make sure that the fesult matches the standard conformance
    if (!expectedDiff1.contains("~") && !expectedDiff2.contains("~")) {
      assertTrue(s"Must conform: ${tpe1.presentableText}, ${tpe2.presentableText}", tpe2.conforms(tpe1))
    } else {
      assertFalse(s"Must not conform: ${tpe1.presentableText}, ${tpe2.presentableText}", tpe2.conforms(tpe1))
    }

    // Also make sure that the diff matches the standard presentation
    assertEquals("Incorrect presentation (1)", tpe1.presentableText, clean(actualDiffString1))
    assertEquals("Incorrect presentation (2)", tpe2.presentableText, clean(actualDiffString2))

    // Also make sure that the number of diff elements match
    val flattenDiff1 = actualDiff1.flatten
    val flattenDiff2 = actualDiff2.flatten
    assertEquals(flattenDiff1.mkString("|") + "\n" + flattenDiff1.mkString("|"), flattenDiff1.length, flattenDiff2.length)
  }

  private def clean(diff: String) = diff.replaceAll("~", "")

  private def asString(diff: TypeDiff) = {
    val parts = diff.flatten.map {
      case Match(text, _) => text
      case Mismatch(text, _) => s"~$text~"
    }
    parts.mkString
  }

  private def typesIn(context: String, type1: String, type2: String): (ScType, ScType) = {
    val Seq(tpe1, tpe2) = s"$context; null: $type1; null: $type2".parse.children.instancesOf[ScTypedExpression].toSeq.map(typeOf)
    (tpe1, tpe2)
  }

  private def typeOf(e: ScTypedExpression) = {
    val typeElement = e.typeElement.getOrElse(throw new IllegalArgumentException("No type element: " + e.getText))
    typeElement.`type`() match {
      case Failure(cause) => throw new IllegalArgumentException("Cannot compute type: " + typeElement.getText + ": " + cause)
      case Right(tpe) => tpe
    }
  }
}
