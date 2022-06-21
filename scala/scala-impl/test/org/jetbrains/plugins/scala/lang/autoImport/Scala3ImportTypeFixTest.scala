package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportTypeFix
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3ImportTypeFixTest
  extends ImportElementFixTestBase[ScReference] {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  override def createFix(ref: ScReference) =
    Option(ref).map(ScalaImportTypeFix(_))

  def testClass(): Unit = checkElementsToImport(
    s"""object Source:
       |  class Foo
       |
       |object Target:
       |  val foo = ${CARET}Foo()
       |""".stripMargin,

    "Source.Foo"
  )

  def testClassInsideGivenImport(): Unit = doTest(
    fileText =
      s"""
         |object Test:
         |  import Givens.given F${CARET}oo
         |
         |object Givens:
         |  class Foo
         |  given Foo = Foo()
         |""".stripMargin,
    expectedText =
      """
        |import Givens.Foo
        |
        |object Test:
        |  import Givens.given Foo
        |
        |object Givens:
        |  class Foo
        |  given Foo = Foo()
        |""".stripMargin,

    "Givens.Foo"
  )
}
