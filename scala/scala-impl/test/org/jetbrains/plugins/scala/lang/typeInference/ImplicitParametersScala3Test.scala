package org.jetbrains.plugins.scala.lang.typeInference
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ImplicitParametersScala3Test extends ImplicitParametersTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3

  def testSCL21117(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Main:
       |  trait T0 { def foo0: String = ??? }
       |  trait T1 extends T0 { def foo1: String = ??? }
       |  trait T2 extends T0 { def foo2: String = ??? }
       |
       |  implicit val b: T1 & T2 = new T1 with T2 {}
       |
       |object Other1:
       |  import Main.*
       |  summon[T1 | T2]
       |  summon[T1 & T2]
       |
       |object Other2:
       |  ${START}summon[Main.T1 | Main.T2]$END
       |""".stripMargin
  )

  def testSCL21117_2(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Main:
       |  trait T0 { def foo0: String = ??? }
       |  trait T1 extends T0 { def foo1: String = ??? }
       |  trait T2 extends T0 { def foo2: String = ??? }
       |
       |  implicit val b: T1 & T2 = new T1 with T2 {}
       |
       |object Other2:
       |  ${START}summon[Main.T1 & Main.T2]$END
       |""".stripMargin
  )
}
