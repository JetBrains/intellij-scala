package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportTypeFix
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression


class Scala3ImportTypeFixTest
  extends ImportElementFixTestBase[ScReferenceExpression] {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  override def createFix(ref: ScReferenceExpression) =
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
}
