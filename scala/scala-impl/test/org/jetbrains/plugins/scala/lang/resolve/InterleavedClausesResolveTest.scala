package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class InterleavedClausesResolveTest extends SimpleResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_7

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
