package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class PackageObjectTestBase extends SimpleResolveTestBase

class PackageObjectTest_before_3_7 extends PackageObjectTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version < LatestScalaVersions.Scala_3_7

  def testReferencingPackageObject(): Unit = testNoResolve(
    s"""
       |package object Test {
       |  val x = 0
       |}
       |
       |object Blub {
       |  val test = Test
       |  test.${REFSRC}x
       |}
       |""".stripMargin)
}

class PackageObjectTest_after_3_7 extends PackageObjectTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_7

  // SCL-23743
  def testReferencingPackageObject(): Unit = doResolveTest(
    s"""
       |package object Test {
       |  val ${REFTGT}x = 0
       |}
       |
       |object Blub {
       |  val test = Test
       |  test.${REFSRC}x
       |}
       |""".stripMargin)
}
