package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Sc3TypedPatternTypeInferenceTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSCL19565(): Unit = doTest(
    s"""
       |object A {
       |  case class Test(data: String, id: Int)
       |  val test: Test = ???
       |
       |  test match {
       |    case test1: Test =>
       |    val x = test1
       |    ${START}x$END
       |  }
       |}
       |//A.Test
       |""".stripMargin
  )

  def testNestedTypedPattern(): Unit = doTest(
    s"""
       |object A {
       |  case class Test(data: String, id: Int)
       |  trait Foo
       |  val test: Test = ???
       |  test match {
       |    case (test: Test): Foo =>
       |    val x = test
       |    ${START}x$END
       |  }
       |}
       |//A.Test with A.Foo
       |""".stripMargin
  )

  def testComplexPattern(): Unit = doTest(
    s"""
       |object A {
       |  case class Test(data: String, id: Int)
       |  val test: Test = ???
       |  trait Foo
       |  trait Bar
       |
       |  123 match {
       |    case ((xx @ Test(d, i)): Foo): Bar =>
       |    val x = xx
       |    ${START}x$END
       |  }
       |}
       |//123 with A.Bar with A.Foo with A.Test
       |""".stripMargin
  )
}
