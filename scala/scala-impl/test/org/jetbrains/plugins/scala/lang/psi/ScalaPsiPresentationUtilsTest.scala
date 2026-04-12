package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.ScalaVersion
import org.junit.Assert.assertEquals

class ScalaPsiPresentationUtilsTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testMethodPresentableTextWithInterleavedClauses(): Unit = {
    configureScalaFromFileText(
      """class A:
        |  def foo[T](x: T)[U](u: U): U = u
        |""".stripMargin
    )

    val function = getFile.elements.findByType[ScFunction].get
    val text = ScalaPsiPresentationUtils.methodPresentableText(function)

    assertEquals("foo[T](x: T)[U](u: U): U", text)
  }
}
