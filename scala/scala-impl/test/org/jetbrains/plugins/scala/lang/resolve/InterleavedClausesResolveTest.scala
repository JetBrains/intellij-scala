package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert._

import scala.jdk.CollectionConverters._

class InterleavedClausesResolveTest extends SimpleResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  def test_previous_type_parameter_visible_in_term_clause(): Unit = doResolveTest(
    s"""def foo[${REFTGT}T](x: ${REFSRC}T)[U](u: U): U = u"""
  )

  def test_later_type_parameter_not_visible_in_previous_term_clause(): Unit = testNoResolve(
    s"""def foo[T](x: ${REFSRC}U)[U](u: U): U = u"""
  )

  def test_previous_term_parameter_visible_in_type_clause(): Unit = doResolveTest(
    s"""def foo[T](${REFTGT}x: T)[U <: ${REFSRC}x.type](u: U): U = u"""
  )

  def test_later_term_parameter_not_visible_in_previous_type_clause(): Unit = testNoResolve(
    s"""def foo[T](x: T)[U <: ${REFSRC}v.type](u: U, v: T): U = u"""
  )

  def test_previous_interleaved_type_parameter_visible_in_later_term_clause(): Unit = doResolveTest(
    s"""def foo[T](x: T)[${REFTGT}U](u: ${REFSRC}U): U = u"""
  )

  def test_last_type_parameter_clause_visible_in_return_type(): Unit = doResolveTest(
    s"""def foo[T](x: T)[${REFTGT}U]: ${REFSRC}U = ???"""
  )

  def test_interleaved_type_arguments_in_method_call_resolve_target(): Unit = doResolveToTargetWithoutProblems(
    s"""def foo[T](x: T): T = x
       |def ${REFTGT}foo[T](x: T)[U](u: U): U = u
       |val x = ${REFSRC}foo[Int](1)[String]("value")""".stripMargin
  )

  // TODO[SIP-47]: requires cross-clause type-parameter propagation during applicability checks
  def disabled_interleaved_type_arguments_with_multiple_term_clauses_resolve_target(): Unit = doResolveToTargetWithoutProblems(
    s"""def foo[T](x: T)[U](u: U): U = u
       |def ${REFTGT}foo[T](x: T)[U](u: U)(v: T): U = u
       |val x = ${REFSRC}foo[Int](1)[String]("value")(2)""".stripMargin
  )

  def test_extension_type_arguments_mapped_by_clause(): Unit = doResolveToTargetWithoutProblems(
    s"""extension [A](a: A)
       |  def ${REFTGT}foo[B](b: B): (A, B) = (a, b)
       |val x = ${REFSRC}foo[Int](1)[String]("value")""".stripMargin
  )

  def test_named_argument_in_interleaved_value_clause_resolves_to_parameter(): Unit = doResolveTest(
    s"""def foo[T](first: T)[U](${REFTGT}secondParam: U): Unit = ()
       |foo[Int](1)[String](${REFSRC}secondParam = "value")""".stripMargin
  )

  def test_named_type_argument_in_interleaved_type_clause_resolves_to_type_parameter(): Unit = doResolveTest(
    s"""import scala.language.experimental.namedTypeArguments
       |def foo[T](first: T)[${REFTGT}U](second: U): Unit = ()
       |foo[Int](1)[${REFSRC}U = String]("value")""".stripMargin
  )

  def test_named_type_argument_in_interleaved_type_clause_after_omitted_type_arguments(): Unit = doResolveTest(
    s"""import scala.language.experimental.namedTypeArguments
       |def foo[T](first: T)[${REFTGT}U](second: U): Unit = ()
       |foo(1)[${REFSRC}U = String]("value")""".stripMargin
  )

  def test_named_type_argument_in_interleaved_type_clause_after_omitted_using_clause(): Unit = doResolveToTargetWithoutProblems(
    s"""import scala.language.experimental.namedTypeArguments
       |given Int = 1
       |def ${REFTGT}foo(first: Int)(using Int)[A](second: A): A = second
       |val x: Int = ${REFSRC}foo(1)[A = Int](2)""".stripMargin
  )

  private def doResolveToTargetWithoutProblems(source: String)(implicit opts: SrcTgtOptions): Unit = {
    val (src, expectedTarget) = setupResolveTest(None, source -> "dummy.scala")

    val reference: ScReference = src match {
      case ref: ScReference => ref
      case _ =>
        throw new AssertionError(s"Expected ScReference, got: ${src.getClass.getName}")
    }

    val resolveResult = reference.bind().orNull
    assertNotNull("Expected single resolve result", resolveResult)
    assertEquals(expectedTarget, resolveResult.element)
    assertTrue(
      s"Expected no resolve problems, got: ${resolveResult.problems.mkString("(", ", ", ")")}",
      resolveResult.problems.isEmpty
    )

    val annotatorErrors = myFixture
      .doHighlighting()
      .asScala
      .filter(_.getSeverity == HighlightSeverity.ERROR)
      .map(info => s"${info.getDescription} [${info.getStartOffset}, ${info.getEndOffset}]")

    assertTrue(
      s"Expected no annotator errors, got:\n${annotatorErrors.mkString("\n")}",
      annotatorErrors.isEmpty
    )
  }
}

class InterleavedClausesResolveWithNamedContextBoundsTest extends SimpleResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_6

  def test_previous_named_context_bound_visible_in_later_term_clause(): Unit = doResolveTest(
    s"""trait TC[A]
       |def foo[T: TC as ${REFTGT}tc](x: ${REFSRC}tc.type)[U](u: U): U = u""".stripMargin
  )(SrcTgtOptions(targetIsLeaf = true))

  def test_previous_named_context_bound_visible_in_later_type_clause(): Unit = doResolveTest(
    s"""trait TC[A]
       |def foo[T: TC as ${REFTGT}tc](x: T)[U <: ${REFSRC}tc.type](u: U): U = u""".stripMargin
  )(SrcTgtOptions(targetIsLeaf = true))

  def test_previous_named_context_bound_visible_in_explicit_using_clause(): Unit = doResolveTest(
    s"""trait TC[A]
       |def foo[T: TC as ${REFTGT}tc](using x: ${REFSRC}tc.type)(y: Int): Int = y""".stripMargin
  )(SrcTgtOptions(targetIsLeaf = true))

  def test_named_context_bound_type_member_visible_in_later_using_clause(): Unit = doResolveTest(
    s"""trait Foo[A] { type ${REFTGT}Out }
       |def foo[A: Foo as fa, B: Foo as fb](a: Int)(using fa.${REFSRC}Out)(using Int): Int = 1""".stripMargin
  )

  def test_later_named_context_bound_not_visible_in_previous_term_clause(): Unit = testNoResolve(
    s"""trait TC[A]
       |def foo[T](x: ${REFSRC}tc.type)[U: TC as tc](u: U): U = u""".stripMargin
  )

  def test_last_named_context_bound_visible_in_return_type(): Unit = doResolveTest(
    s"""trait TC[A]
       |def foo[T](x: T)[U: TC as ${REFTGT}tc]: ${REFSRC}tc.type = ???""".stripMargin
  )(SrcTgtOptions(targetIsLeaf = true))
}
