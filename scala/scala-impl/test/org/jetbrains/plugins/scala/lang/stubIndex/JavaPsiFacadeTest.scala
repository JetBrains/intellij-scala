package org.jetbrains.plugins.scala
package lang.stubIndex

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.junit.Assert
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
class JavaPsiFacadeTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testJavaPsiFacade(): Unit = {
    configureFromFileText(
      """package test
        |
        |class A
        |object A
        |
        |trait B
        |object B
        |
        |class +++
        |object +++
        |
        |object X {
        |  class `bla bla`
        |  object `bla bla`
        |}
        |
        |class Y {
        |  class Z
        |}
        |""".stripMargin)

    val facade = JavaPsiFacade.getInstance(getProject)
    def assertSingleClassFound(qualifiedName: String): Unit = {
      val classes = facade.findClasses(qualifiedName, GlobalSearchScope.projectScope(getProject))
      Assert.assertTrue(s"Single class expected for name $qualifiedName, found: ${classes.mkString}", classes.size == 1)
    }

    assertSingleClassFound("test.A")
    assertSingleClassFound("test.A$")
    assertSingleClassFound("test.B")
    assertSingleClassFound("test.B$")
    assertSingleClassFound("test.$plus$plus$plus$")
    assertSingleClassFound("test.$plus$plus$plus$")
    assertSingleClassFound("test.Y.Z")

    val blablaClass = "test.X." + ScalaNamesUtil.toJavaName("`bla bla`")
    val blablaObject = blablaClass + "$"
    assertSingleClassFound(blablaClass)
    assertSingleClassFound(blablaObject)
  }
}
