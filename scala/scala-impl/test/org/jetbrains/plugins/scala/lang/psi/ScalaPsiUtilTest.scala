package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.junit.Assert.assertTrue

import scala.collection.immutable.ListSet

class ScalaPsiUtilTest extends ScalaLightCodeInsightFixtureTestCase {

  def testIsInsideImportExpression(): Unit = {
    val textWithImports =
      """import scala.util.Random
        |import scala.util.{
        |   Random,
        |   ChainingOps => ChainingOpsRenamed,
        |   Either => _,
        |   _
        |}
        |""".stripMargin

    getFixture.configureByText("a.scala", textWithImports)

    val importExpressions = getFile.elements.filterByType[ScImportExpr].toSeq
    val elementsInsideImports = importExpressions.flatMap(_.depthFirst()).to(ListSet) -- importExpressions
    elementsInsideImports.foreach { child =>
      assertTrue(
        s"Element at range ${child.getTextRange} with text `${child.getText}` is in import expression but isInsideImportExpression returned false",
        ScalaPsiUtil.isInsideImportExpression(child)
      )
    }
  }
}