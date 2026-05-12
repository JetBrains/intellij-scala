package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}
import org.junit.Assert

class ExpectedTypeDrivenOverloadingResolutionTest extends SimpleResolveTestBase {

  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3

  def testSCL16251(): Unit = {
    val (src, _) = setupResolveTest(
      None,
      s"""
         |val xs: Array[BigInt] = Arr${REFSRC}ay(1, 2, 3)
         |""".stripMargin -> "Test.scala"
    )

    val result = src.resolve()
    result match {
      case fn: ScFunctionDefinition =>
        fn.`type`()
          .foreach(tpe => Assert.assertEquals("T => ClassTag[T] => Array[T]", tpe.presentableText(TypePresentationContext.emptyContext, Context.Empty)))
      case _ => Assert.fail("Invalid resolve result.")
    }
  }

  def testExpectedTypeFilteringDuringShapeResolve(): Unit = doResolveTest(
    s"""object Usage {
       |  trait Foo
       |
       |  def foo(x: Int): Int = 1
       |  def foo(x: String): Int = 2
       |  def foo(x: Double): String = ""
       |  def fo${REFTGT}o(x: Foo): String = ???
       |
       |  implicit def string2Int(s: String): Int = 123
       |  val z: Int = f${REFSRC}oo(new Foo {})
       |}""".stripMargin
  )
}
