package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class TraitParameterResolveTest extends SimpleResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_7

  def test_simple(): Unit = doResolveTest(
    s"""
       |trait Test(${REFTGT}i: Int) {
       |  def foo = ${REFSRC}i
       |}
       |""".stripMargin
  )

  def test_inheritance(): Unit = doResolveTest(
    s"""
       |trait Base(val ${REFTGT}i: Int)
       |
       |trait Impl extends Base {
       |  def foo = ${REFSRC}i
       |}
       |""".stripMargin
  )

  def test_named_bound(): Unit = doResolveTest(
    s"""
       |trait TC[X]
       |
       |trait Test[T : TC as ${REFTGT}x] {
       |  def foo = ${REFSRC}x
       |}
       |""".stripMargin
  )(SrcTgtOptions(targetIsLeaf = true))

  def test_named_bound_with_normal_param(): Unit = doResolveTest(
    s"""
       |trait TC[X]
       |
       |trait Test[T : TC as ${REFTGT}x](i: Int) {
       |  def foo = ${REFSRC}x
       |}
       |""".stripMargin
  )(SrcTgtOptions(targetIsLeaf = true))
}
