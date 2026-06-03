package org.jetbrains.plugins.scala
package codeInsight
package hints

class Scala3OpaqueTypeHintTest extends InlayHintsTestBase {
  import Hint.{End => E, Start => S}

  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_3

  override protected def setUp(): Unit = {
    super.setUp()
    scalaFixture.setFileTextPatcher(identity)
  }

  private def doTest(text: String): Unit = try {
    ScalaHintsSettings.xRayMode = true
    doInlayTest(text)
  } finally {
    ScalaHintsSettings.xRayMode = false
  }

  def testDefinition(): Unit = doTest(
    s"""object Inside:
       |  opaque type T = (String, Int)
       |object Outside:
       |  import Inside.*
       |  val x$S: T$E = ??? : T""".stripMargin)

  def testParameter(): Unit = doTest(
    s"""object Inside:
       |  opaque type T = (String, Int)
       |object Outside:
       |  import Inside.*
       |  (??? : Seq[T]).map($S(${E}x$S: T)$E => x.toString)""".stripMargin)

  def testUnderscore(): Unit = doTest(
    s"""object Inside:
       |  opaque type T = (String, Int)
       |object Outside:
       |  import Inside.*
       |  (??? : Seq[T]).map($S(${E}_$S: T)$E.toString)""".stripMargin)

  def testPattern(): Unit = doTest(
    s"""object Inside:
       |  opaque type T = (String, Int)
       |object Outside:
       |  import Inside.*
       |  (??? : Seq[T]).map { case x$S: T$E => x.toString }""".stripMargin)

  def testChain(): Unit = doTest(
    s"""object Inside:
       |  opaque type T = (String, Int)
       |object Outside:
       |  import Inside.*
       |  (??? : Seq[T])$S: Seq[T]$E
       |    .map((_: T).toString)$S: Seq[String]$E
       |    .map((_: String).length)$S: Seq[Int]$E
       |  ()""".stripMargin)
}
