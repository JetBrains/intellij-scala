package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportExpr, ScImportSelector}
import org.junit.Assert.{assertEquals, assertFalse, assertNull, assertSame, assertTrue}

import scala.jdk.CollectionConverters._

class ImportAndExportPsiUtilsTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testFindParentImportExpressionForDirectReferenceAndSelectors(): Unit = {
    configureScalaFromFileText(
      """import fixture.direct
        |import fixture.{selected, renamed as alias, hidden as _, *}
        |""".stripMargin
    )

    val expressions = importExpressions
    val directExpression = expressions.head
    val selectorExpression = expressions(1)
    val directName = directExpression.reference.get.nameId
    val selectedName = selectorNamed(selectorExpression, "selected").reference.get.nameId
    val aliasName = selectorNamed(selectorExpression, "renamed").aliasNameElement.get
    val hiddenName = selectorNamed(selectorExpression, "hidden").reference.get.nameId

    assertParentExpression(directName, directExpression)
    assertParentExpression(selectedName, selectorExpression)
    assertParentExpression(aliasName, selectorExpression)
    assertParentExpression(hiddenName, selectorExpression)
  }

  def testFindParentImportExpressionExcludesTheExpressionItselfAndUnrelatedCode(): Unit = {
    configureScalaFromFileText(
      """import fixture.direct
        |
        |object Sample:
        |  def method(): Unit =
        |    val local = 1
        |""".stripMargin
    )

    val expression = importExpressions.head
    val importKeyword = getFile.findElementAt(0)
    val local = getFile.findElementAt(getFile.getText.indexOf("local"))

    assertNoParentExpression(expression)
    assertNoParentExpression(importKeyword)
    assertNoParentExpression(local)
    assertNoParentExpression(null)
  }

  def testFindExplicitExportForDirectAndBracedNamedExports(): Unit = {
    configureScalaFromFileText(
      """object Sample:
        |  export delegate.direct
        |  export delegate.{selected, renamed as alias}
        |""".stripMargin
    )

    val exports = exportStatements
    val directExport = exports.head
    val selectedExport = exports(1)
    val directName = directExport.importExprs.head.reference.get.nameId
    val selectedName = selectorNamed(selectedExport, "selected").reference.get.nameId
    val aliasName = selectorNamed(selectedExport, "renamed").aliasNameElement.get

    assertEquals(Some(directExport -> "direct"), ImportAndExportPsiUtils.findExplicitExport(directName))
    assertEquals(Some(selectedExport -> "selected"), ImportAndExportPsiUtils.findExplicitExport(selectedName))
    assertEquals(Some(selectedExport -> "alias"), ImportAndExportPsiUtils.findExplicitExport(aliasName))
  }

  def testFindExplicitExportForScala2StyleAlias(): Unit = {
    configureScalaFromFileText(
      """object Sample:
        |  export delegate.{renamed => alias}
        |""".stripMargin
    )

    val exportStatement = exportStatements.head
    val aliasName = selectorNamed(exportStatement, "renamed").aliasNameElement.get

    assertEquals(Some(exportStatement -> "alias"), ImportAndExportPsiUtils.findExplicitExport(aliasName))
  }

  def testFindExplicitExportExcludesSourceNamesAndNonNamedExports(): Unit = {
    configureScalaFromFileText(
      """object Sample:
        |  import imported.direct
        |  export delegate.{renamed as alias, hidden as _, *, given Ordering[String]}
        |  val unrelated = 1
        |""".stripMargin
    )

    val importName = importExpressions.head.reference.get.nameId
    val exportStatement = exportStatements.head
    val sourceName = selectorNamed(exportStatement, "renamed").reference.get.nameId
    val hiddenSelector = selectorNamed(exportStatement, "hidden")
    val hiddenName = hiddenSelector.reference.get.nameId
    val hidingAliasName = hiddenSelector.aliasNameElement.get
    val exportQualifierName = exportStatement.importExprs.head.reference.get.nameId
    val wildcard = exportStatement.importExprs.head.selectors.find(_.isWildcardSelector).get.wildcardElement.get
    val givenSelector = exportStatement.importExprs.head.selectors.find(_.isGivenSelector).get
    val unrelated = getFile.findElementAt(getFile.getText.indexOf("unrelated"))

    assertEquals(None, ImportAndExportPsiUtils.findExplicitExport(importName))
    assertEquals(None, ImportAndExportPsiUtils.findExplicitExport(sourceName))
    assertEquals(None, ImportAndExportPsiUtils.findExplicitExport(hiddenName))
    assertEquals(None, ImportAndExportPsiUtils.findExplicitExport(hidingAliasName))
    assertEquals(None, ImportAndExportPsiUtils.findExplicitExport(exportQualifierName))
    assertEquals(None, ImportAndExportPsiUtils.findExplicitExport(wildcard))
    assertEquals(None, ImportAndExportPsiUtils.findExplicitExport(givenSelector))
    assertEquals(None, ImportAndExportPsiUtils.findExplicitExport(unrelated))
  }

  private def assertParentExpression(
    element: PsiElement,
    expected: ScImportExpr
  ): Unit = {
    assertSame(expected, ImportAndExportPsiUtils.findParentImportExpression(element).orNull)
    assertSame(expected, ImportAndExportPsiUtils.findParentImportExpressionOrNull(element))
    assertTrue(ImportAndExportPsiUtils.isInsideImportExpression(element))

    // ScalaPsiUtil remains the compatibility entry point for existing callers.
    assertSame(expected, ScalaPsiUtil.parentImportExpression(element).orNull)
    assertSame(expected, ScalaPsiUtil.getParentImportExpression(element))
    assertTrue(ScalaPsiUtil.isInsideImportExpression(element))
  }

  private def assertNoParentExpression(element: PsiElement): Unit = {
    assertTrue(ImportAndExportPsiUtils.findParentImportExpression(element).isEmpty)
    assertFalse(ImportAndExportPsiUtils.isInsideImportExpression(element))
    assertNull(ImportAndExportPsiUtils.findParentImportExpressionOrNull(element))
  }

  private def importExpressions: Seq[ScImportExpr] =
    PsiTreeUtil.findChildrenOfType(getFile, classOf[ScImportExpr]).asScala.toSeq

  private def exportStatements: Seq[ScExportStmt] =
    PsiTreeUtil.findChildrenOfType(getFile, classOf[ScExportStmt]).asScala.toSeq

  private def selectorNamed(exportStatement: ScExportStmt, referenceName: String): ScImportSelector =
    selectorNamed(exportStatement.importExprs.head, referenceName)

  private def selectorNamed(importExpr: ScImportExpr, referenceName: String): ScImportSelector =
    importExpr.selectors.find(_.reference.exists(_.refName == referenceName)).get
}
