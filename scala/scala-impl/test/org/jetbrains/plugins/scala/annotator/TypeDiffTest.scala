package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.Tree.{Leaf, Node}
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Match, Mismatch}
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.junit.Assert._
import org.junit.experimental.categories.Category

// TODO Work in progress
@Category(Array(classOf[TypecheckerTests]))
class TypeDiffTest extends ScalaFixtureTestCase {
  // TODO test separation of matched elements, such as [ or ]

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  /* TODO:
      compound types (comparison)
      structural types (comparison)
      existential types (comparison)
      java array types
    */

  def testSingularParsing(): Unit = {
    assertParsedAs(
      "class Foo",
      "Foo", "<Foo>")
  }

  def testSingular(): Unit = {
    assertDiffsAre(
      "class Foo",
      "Foo", "Foo"
    )
    assertDiffsAre(
      "class Foo; class Bar",
      "~Foo~", "~Bar~"
    )

    // Subtyping
    assertDiffsAre(
      "class Foo; class Bar extends Foo",
      "Foo", "Bar"
    )
    assertDiffsAre(
      "class Foo; class Bar extends Foo",
      "~Bar~", "~Foo~"
    )
  }

  def testQualifier(): Unit = {
    assertDiffsAre(
      "",
      "String", "String"
    )

    assertDiffsAre(
      "",
      "~Long~", "~java.lang.Long~",
      verifyPresentation = false
    )
  }

  def testLiteralParsing(): Unit = {
    assertParsedAs(
      "",
      "1", "<1>")
  }

  def testLiteral(): Unit = {
    // Equal
    assertDiffsAre(
      "",
      "1", "1"
    )
    assertDiffsAre(
      "",
      "true", "true"
    )
    assertDiffsAre(
      "",
      "1.0f", "1.0f"
    )
    assertDiffsAre(
      "",
      "1.0", "1.0"
    )
    assertDiffsAre(
      "",
      "'a'", "'a'"
    )
    assertDiffsAre(
      "",
      "\"foo\"", "\"foo\""
    )

    // Not equal
    assertDiffsAre(
      "",
      "~1~", "~2~"
    )
    assertDiffsAre(
      "",
      "~true~", "~false~"
    )
    assertDiffsAre(
      "",
      "~1.0f~", "~2.0f~"
    )
    assertDiffsAre(
      "",
      "~1.0~", "~2.0~"
    )
    assertDiffsAre(
      "",
      "~'a'~", "~'b'~"
    )
    assertDiffsAre(
      "",
      "~\"foo\"~", "~\"bar\"~"
    )

    // Type differs
    assertDiffsAre(
      "",
      "~true~", "~1~"
    )
  }

  def testParameterizedParsing(): Unit = {
    assertParsedAs(
      "class A; class Foo[T]",
      "Foo[A]", "<<Foo>[<<A>>]>")
    assertParsedAs(
      "class A; class B; class Foo[T1, T2]",
      "Foo[A, B]", "<<Foo>[<<A>, <B>>]>")
  }

  def testParameterized(): Unit = {
    assertDiffsAre(
      "class A; class Foo[T]",
      "Foo[A]", "Foo[A]"
    )
    assertDiffsAre(
      "class A; class B; class Foo[T1, T2]",
      "Foo[A, B]", "Foo[A, B]"
    )

    // Base type
    assertDiffsAre(
      "class A; class Foo[T]; class Bar[T]",
      "~Foo~[A]", "~Bar~[A]"
    )
    assertDiffsAre(
      "class A; class Foo[T]; class Bar[T] extends Foo[T]",
      "Foo[A]", "Bar[A]"
    )
    assertDiffsAre(
      "class A; class Foo[T]; class Bar[T] extends Foo[T]",
      "~Bar~[A]", "~Foo~[A]"
    )
    // TODO argument / parameter correspondence, additional parameters

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

  def testWildcardParsing(): Unit = {
    assertParsedAs(
      "class A; class Foo[T]",
      "Foo[_ <: A]", "<<Foo>[<<_ <: <A>>>]>")
    assertParsedAs(
      "class A; class Foo[T]",
      "Foo[_ >: A]", "<<Foo>[<<_ >: <A>>>]>")
    assertParsedAs(
      "class A; class B; class Foo[T]",
      "Foo[_ >: A <: B]", "<<Foo>[<<_ >: <A> <: <B>>>]>")

    // Multiple
    assertParsedAs(
      "class A; class B; class Foo[T1, T2]",
      "Foo[_ <: A, _ <: B]", "<<Foo>[<<_ <: <A>>, <_ <: <B>>>]>")
  }

// TODO def testWildcard(): Unit = {

  def testTupleParsing(): Unit = {
    assertParsedAs(
      "class A; class B",
      "(A, B)", "<(<<A>, <B>>)>")
  }

  def testTuple(): Unit = {
    assertDiffsAre(
      "class A; class B",
      "(A, B)", "(A, B)"
    )
    assertDiffsAre(
      "class A; class B; class C",
      "(~A~, B)", "(~C~, B)"
    )
    assertDiffsAre(
      "class A; class B; class C",
      "(A, ~B~)", "(A, ~C~)"
    )

    // Covariance
    assertDiffsAre(
      "class A; class B; class C extends A",
      "(A, B)", "(C, B)"
    )
    assertDiffsAre(
      "class A; class B; class C extends A",
      "(~C~, B)", "(~A~, B)"
    )

    // Argument count
    assertDiffsAre(
      "class A; class B; class C",
      "~(A, B)~", "~(A, B, C)~" // TODO parse nested types? (create matching placeholders?)
    )
    assertDiffsAre(
      "class A; class B; class C",
      "~(A, B, C)~", "~(A, B)~"
    )

    // Nesting
    assertDiffsAre(
      "class A; class B; class C",
      "((A, ~B~), A)", "((A, ~C~), A)"
    )
    assertDiffsAre(
      "class A; class B; class C",
      "(A, (A, ~B~))", "(A, (A, ~C~))"
    )

    // Not tuple
    assertDiffsAre(
      "class A; class B; class C",
      "~A~", "~(A, A)~"
    )
  }

  def testInfixParsing(): Unit = {
    assertParsedAs(
      "class A; class B; class &[T1, T2]",
      "A & B", "<<A> <&> <B>>")
  }

  def testInfix(): Unit = {
    assertDiffsAre(
      "class A; class B; class &[T1, T2]",
      "A & B", "A & B"
    )

    // Base
    assertDiffsAre(
      "class A; class B; class &[T1, T2]; class &&[T1, T2] extends &[T1, T2]",
      "A & B", "A && B"
    )
    assertDiffsAre(
      "class A; class B; class &[T1, T2]; class &&[T1, T2] extends &[T1, T2]",
      "A ~&&~ B", "A ~&~ B"
    )
    // TODO argument / parameter correspondence
    // TODO infix vs parameterized

    // Equivalence
    assertDiffsAre(
      "class A; class B; class C; class &[T1, T2]",
      "~A~ & B", "~C~ & B"
    )
    assertDiffsAre(
      "class A; class B; class C; class &[T1, T2]",
      "A & ~B~", "A & ~C~"
    )

    // Invariance
    assertDiffsAre(
      "class A; class B; class C extends A; class &[T1, T2]",
      "~A~ & B", "~C~ & B"
    )
    assertDiffsAre(
      "class A; class B; class C extends A; class &[T1, T2]",
      "~C~ & B", "~A~ & B"
    )

    // Covariance
    assertDiffsAre(
      "class A; class B; class C extends A; class &[+T1, T2]",
      "A & B", "C & B"
    )
    assertDiffsAre(
      "class A; class B; class C extends A; class &[+T1, T2]",
      "~C~ & B", "~A~ & B"
    )

    // Contravariance
    assertDiffsAre(
      "class A; class B; class C extends A; class &[-T1, T2]",
      "~A~ & B", "~C~ & B"
    )
    assertDiffsAre(
      "class A; class B; class C extends A; class &[-T1, T2]",
      "C & B", "A & B"
    )

    // Nesting
    assertDiffsAre(
      "class A; class B; class C; class &[T1, T2]; class &&[T1, T2]",
      "A & B && C", "A & B && C"
    )
  }

  def testFunctionParsing(): Unit = {
    assertParsedAs(
      "class R",
      "() => R", "<() => <R>>")
    assertParsedAs(
      "class P; class R",
      "P => R", "<<P> => <R>>")
    assertParsedAs(
      "class P1; class P2; class R",
      "(P1, P2) => R", "<(<<P1>, <P2>>) => <R>>")
    assertParsedAs(
      "class P; class R1; class R2",
      "(P => R1) => R2", "<(<<P> => <R1>>) => <R2>>")
    assertParsedAs(
      "class P1; class P2; class R",
      "((P1, P2)) => R", "<(<(<<P1>, <P2>>)>) => <R>>")
  }

  def testFunction(): Unit = {
    assertDiffsAre(
      "class P; class R",
      "P => R", "P => R"
    )
    assertDiffsAre(
      "class P1; class P2; class R",
      "(P1, P2) => R", "(P1, P2) => R"
    )

    // Base type
//    assertDiffsAre(
//      "class P; class R; class F[A, B] extends Function1[A, B]",
//      "P => R", "F[P, R]",
//      "Function1[P, R", "F[P, R]"
//    )

    // Parameter type conformance
    assertDiffsAre(
      "class P1; class P2 extends P1; class R",
      "P2 => R", "P1 => R"
    )
    assertDiffsAre(
      "class P1; class P2; class R",
      "~P1~ => R", "~P2~ => R"
    )
    assertDiffsAre(
      "class P1; class P2 extends P1; class R",
      "~P1~ => R", "~P2~ => R"
    )

    // Multiple parameter types
    assertDiffsAre(
      "class P1; class P2; class P3 extends P1; class R",
      "(~P2~, P3) => R", "(~P1~, P1) => R"
    )
    assertDiffsAre(
      "class P1; class P2; class P3 extends P1; class R",
      "(P3, ~P2~) => R", "(P1, ~P1~) => R"
    )

    // Result type conformance
    assertDiffsAre(
      "class P; class R1; class R2 extends R1",
      "P => R1", "P => R2"
    )
    assertDiffsAre(
      "class P; class R1; class R2",
      "P => ~R1~", "P => ~R2~"
    )
    assertDiffsAre(
      "class P; class R1; class R2 extends R1",
      "P => ~R2~", "P => ~R1~"
    )

    // Parameter type and result type
    assertDiffsAre(
      "class P1; class P2; class R1; class R2",
      "~P1~ => ~R1~", "~P2~ => ~R2~"
    )

    // Different parameter count // TODO parse nested types? (create matching placeholders?)
    assertDiffsAre(
      "class P; class R",
      "~()~ => R", "~P~ => R"
    )
    assertDiffsAre(
      "class P; class R",
      "~P~ => R", "~()~ => R"
    )
    assertDiffsAre(
      "class P1; class P2; class R",
      "~P1~ => R", "~(P1, P2)~ => R"
    )
    assertDiffsAre(
      "class P1; class P2; class R",
      "~(P1, P2)~ => R", "~P1~ => R"
    )

    // Nesting
    assertDiffsAre(
      "class P1; class P2; class P3; class R",
      "P1 => ~P2~ => R", "P1 => ~P3~ => R"
    )
    assertDiffsAre(
      "class P1; class P2; class R1; class R2",
      "P1 => P2 => ~R1~", "P1 => P2 => ~R2~"
    )
    assertDiffsAre(
      "class P1; class P2; class P3; class R",
      "(P1 => ~P2~) => R", "(P1 => ~P3~) => R"
    )
    assertDiffsAre(
      "class P1; class P2; class R1; class R2",
      "(P1 => P2) => ~R1~", "(P1 => P2) => ~R2~"
    )

    // Tuple parameter type
    assertDiffsAre(
      "class P1; class P2; class P3; class R",
      "(~(P1, P2)~) => R", "~P3~ => R"
    )
    assertDiffsAre(
      "class P1; class P2; class P3; class R",
      "~((P1, P2))~ => R", "~(P1, P2)~ => R"
    )
    assertDiffsAre(
      "class P1; class P2; class P3; class R",
      "((~P1~, ~P2~)) => R", "((~P2~, ~P1~)) => R"
    )

    // Non-function type
    assertDiffsAre(
      "class P; class R; class A",
      "~P => R~", "~A~"
    )
    assertDiffsAre(
      "class P; class R; class A",
      "~A~", "~P => R~"
    )
  }

  def testCompoundParsing(): Unit = {
    // Components
    assertParsedAs(
      "class A; class B",
      "A with B", "<<A> with <B>>")
    assertParsedAs(
      "class A; class B; class C",
      "A with B with C", "<<A> with <B> with <C>>")

    // Refinement
    assertParsedAs(
      "class A",
      "AnyRef{def a: A}", "<<AnyRef>{<<def a: A>>}>") // TODO signature types
    assertParsedAs(
      "class A; class B",
      "AnyRef{def a: A; def b: B}", "<<AnyRef>{<<def a: A>; <def b: B>>}>")

    assertParsedAs(
      "",
      "AnyRef{type T}", "<<AnyRef>{<<type T>>}>")
    assertParsedAs(
      "",
      "AnyRef{type T1; type T2}", "<<AnyRef>{<<type T1>; <type T2>>}>")

    assertParsedAs(
      "class A",
      "AnyRef{def a: A; type T}", "<<AnyRef>{<<def a: A>; <type T>>}>")

    // Components and refinement
    assertParsedAs(
      "class A; class B",
      "A with B {def a: A}", "<<A> with <B>{<<def a: A>>}>")
  }

  // TODO def testCompound(): Unit = {

  // TODO List[List[Int]]
  // TODO FunctionN vs A => B

  private def assertParsedAs(context: String, tpe: String, structure: String): Unit = {
    def asString(diff: Tree[TypeDiff]): String = diff match {
      case Leaf(Match(text, _)) => text
      case Node(diffs @_*) => "<" + diffs.map(asString).mkString + ">"
      case _ => ???
    }
    assertEquals(structure, asString(TypeDiff.parse(typesIn(context, tpe).head)(TypePresentationContext.emptyContext)))
  }

  private def assertDiffsAre(context: String, expectedDiff1: String, expectedDiff2: String, verifyPresentation: Boolean = true): Unit = {
    implicit val tpc: TypePresentationContext = TypePresentationContext.emptyContext
    // Make sure that the expected diffs are coherent
    assertTrue(s"""The number of mismatches must match:
                  |$expectedDiff1
                  |$expectedDiff2""".stripMargin,
      expectedDiff1.count(_ == '~') == expectedDiff2.count(_ == '~'))

    val Seq(tpe1, tpe2) = typesIn(context, clean(expectedDiff1), clean(expectedDiff2))
    val (actualDiffData1, actualDiffData2) = TypeDiff.forBoth(tpe1, tpe2)

    // Make sure that the actual diffs match the expected diffs
    val actualDiff1 = asString(actualDiffData1)
    val actualDiff2 = asString(actualDiffData2)
    assertEquals("Incorrect diff (1)", expectedDiff1, actualDiff1)
    assertEquals("Incorrect diff (2)", expectedDiff2, actualDiff2)

    // Also make sure that the result reflects the standard conformance
    if (!expectedDiff1.contains("~") && !expectedDiff2.contains("~")) {
      assertTrue(s"Must conform: ${tpe1.presentableText}, ${tpe2.presentableText}", tpe2.conforms(tpe1))
    } else {
      assertFalse(s"Must not conform: ${tpe1.presentableText}, ${tpe2.presentableText}", tpe2.conforms(tpe1))
    }

    // Also make sure that the diff matches the standard presentation
    // TODO Remove the condition after implementing SCL-15899 ("java.lang.Long" is not "Long" in Scala)
    if (verifyPresentation) {
      assertEquals("Incorrect presentation (1)", tpe1.presentableText, clean(actualDiff1))
      assertEquals("Incorrect presentation (2)", tpe2.presentableText, clean(actualDiff2))
    }

    // Also make sure that the number of diff elements match (needed to keep alignment in a table)
    val flattenDiff1 = actualDiffData1.flatten
    val flattenDiff2 = actualDiffData2.flatten
    assertEquals(s"""The number of elements must match:
                    |${flattenDiff1.mkString("|")}
                    |${flattenDiff2.mkString("|")}""".stripMargin,
      flattenDiff1.length, flattenDiff2.length)
  }

  private def clean(diff: String) = diff.replaceAll("~", "")

  private def asString(diff: Tree[TypeDiff]) = {
    val parts = diff.flatten.map {
      case Match(text, _) => text
      case Mismatch(text, _) => s"~$text~"
    }
    parts.mkString
  }

  private def typesIn(context: String, types: String*): Seq[ScType] =
    parseText(s"$context; ${types.map("null: " + _).mkString(";")}")
      .children.filterByType[ScTypedExpression]
      .map(typeOf).toSeq

  private def typeOf(e: ScTypedExpression) = {
    val typeElement = e.typeElement.getOrElse(throw new IllegalArgumentException("No type element: " + e.getText))
    typeElement.`type`() match {
      case Failure(cause) => throw new IllegalArgumentException("Cannot compute type: " + typeElement.getText + ": " + cause)
      case Right(tpe) => tpe
    }
  }

  private def parseText(@Language("Scala") s: String): ScalaFile =
    PsiFileFactory.getInstance(getProject).createFileFromText("foo.scala", ScalaFileType.INSTANCE, s).asInstanceOf[ScalaFile]
}
