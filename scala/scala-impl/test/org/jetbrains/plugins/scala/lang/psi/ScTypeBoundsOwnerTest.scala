package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScWildcardTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert._

/**
 * Tests the syntactic lower/upper bound accessors and ownership of their returned PSI.
 * Covers parameters, abstract members and wildcards across comments and refinements,
 * keeping context bounds separate from ordinary type bounds without computing types.
 */
class ScTypeBoundsOwnerTest extends SimpleTestCase {
  def testBoundsAcrossCommentsAndRefinements(): Unit = {
    for (version <- Seq(ScalaVersion.Latest.Scala_2_13, ScalaVersion.Latest.Scala_3_8)) {
      val wildcard = if (version.isScala3) "?" else "_"
      val file = parseScalaFile(
        s"""trait Bounds[T >: /* lower */ Nothing <: // upper
           |  AnyRef] {
           |  type Member >: /* lower */ Nothing <: { def value: Int }
           |  def values: List[$wildcard >: /* lower */ Nothing <: // upper
           |    AnyRef]
           |}
           |""".stripMargin,
        ScalaFeatures.onlyByVersion(version)
      )
      assertTrue(file.depthFirst().filterByType[PsiErrorElement].isEmpty)
      val Seq(parameter) = file.depthFirst().filterByType[ScTypeParam].toSeq
      val Seq(member) = file.depthFirst().filterByType[ScTypeAliasDeclaration].toSeq
      val Seq(wildcardType) = file.depthFirst().filterByType[ScWildcardTypeElement].toSeq
      assertBounds(parameter, "Nothing", "AnyRef")
      assertBounds(member, "Nothing", "{ def value: Int }")
      assertBounds(wildcardType, "Nothing", "AnyRef")
    }
  }

  def testContextBoundsDoNotBecomeOrdinaryBounds(): Unit = {
    val file = parseScalaFile("class Bounds[T: Ordering, U <: AnyRef: Ordering]")
    val Seq(t, u) = file.depthFirst().filterByType[ScTypeParam].toSeq
    assertTrue(t.lowerTypeElement.isEmpty)
    assertTrue(t.upperTypeElement.isEmpty)
    assertTrue(u.lowerTypeElement.isEmpty)
    assertEquals(Some("AnyRef"), u.upperTypeElement.map(_.getText))
    for (parameter <- Seq(t, u)) {
      assertEquals(Seq("Ordering"), parameter.contextBounds.map(_.typeElement.getText))
    }
  }

  private def assertBounds(owner: ScTypeBoundsOwner, lower: String, upper: String): Unit = {
    val lowerElement = owner.lowerTypeElement.get
    val upperElement = owner.upperTypeElement.get
    assertEquals(lower, lowerElement.getText)
    assertEquals(upper, upperElement.getText)
    assertSame(owner, lowerElement.getParent)
    assertSame(owner, upperElement.getParent)
  }
}
