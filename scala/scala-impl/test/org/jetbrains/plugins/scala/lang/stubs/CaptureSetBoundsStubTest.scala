package org.jetbrains.plugins.scala.lang.stubs

import com.intellij.psi.{PsiErrorElement, PsiManager}
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.util.AstLoadingFilter
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, ScalaLightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScTypeAliasStub, ScTypeParamStub}
import org.jetbrains.plugins.scala.lang.stubs.StubSerializationTestUtil.roundTripStubTree
import org.junit.Assert._

import scala.jdk.CollectionConverters._

/**
 * Tests capture-bound ownership through source stub creation, serialization and AST loading.
 * Ordinary type-bound APIs must keep capture syntax out of stored type text and avoid
 * loading the AST; capture accessors deliberately load it and return the correct children.
 * Covers parameters and abstract/opaque aliases, without capture-set type semantics.
 */
class CaptureSetBoundsStubTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_8

  override protected def additionalCompilerOptions: Seq[String] = CaptureCheckingTestUtils.compilerOptions

  def testAbstractAliasCaptureBoundsSurviveStubLifecycle(): Unit = assertAliasCaptureBounds(opaque = false)

  def testOpaqueAliasCaptureBoundsSurviveStubLifecycle(): Unit = assertAliasCaptureBounds(opaque = true)

  private def assertAliasCaptureBounds(opaque: Boolean): Unit = {
    val prefix = if (opaque) "opaque type" else "type"
    val rhs = if (opaque) " = caps.CapSet" else ""
    val virtualFile = myFixture.getTempDirFixture.createFile("Box.scala",
      s"""trait Box {
         |  $prefix Lower >: {} <: caps.CapSet$rhs
         |  $prefix Upper >: caps.CapSet <: {any}$rhs
         |  $prefix Both >: {} <: {any}$rhs
         |  $prefix Ordinary >: Nothing <: caps.CapSet$rhs
         |}
         |""".stripMargin)
    val file = PsiManager.getInstance(getProject).findFile(virtualFile).asInstanceOf[PsiFileImpl]
    assertFalse("Start from stub-backed source PSI", file.isContentsLoaded)

    val stubs = AstLoadingFilter.disallowTreeLoading[Seq[ScTypeAliasStub], Exception] { () =>
      val tree = file.getStubTree
      assertNotNull(tree)
      val aliases = tree.getPlainList.asScala.toSeq.filterByType[ScTypeAliasStub]
      assertStoredAliasBounds(aliases, opaque)
      assertOrdinaryAliasBounds(aliases, opaque)

      val restored = roundTripStubTree(tree)
      assertStoredAliasBounds(restored.getPlainList.asScala.toSeq.filterByType[ScTypeAliasStub], opaque)
      aliases
    }
    assertFalse("Ordinary alias bound APIs must not load capture syntax", file.isContentsLoaded)

    // Capture syntax intentionally loads the AST; the same alias PSI must then retain both bound kinds.
    assertAliasCaptureBounds(stubs.map(_.getPsi))
    assertTrue(file.isContentsLoaded)
    assertTrue(file.depthFirst().filterByType[PsiErrorElement].isEmpty)

    assertOrdinaryAliasBounds(stubs, opaque)
    assertStoredAliasBounds(stubs, opaque)
  }

  private def assertStoredAliasBounds(stubs: Seq[ScTypeAliasStub], opaque: Boolean): Unit = {
    assertEquals(Seq("Lower", "Upper", "Both", "Ordinary"), stubs.map(_.getName))
    assertEquals(Seq(None, Some("caps.CapSet"), None, Some("Nothing")), stubs.map(_.lowerBoundText))
    assertEquals(Seq(Some("caps.CapSet"), None, None, Some("caps.CapSet")), stubs.map(_.upperBoundText))
    for (stub <- stubs) {
      assertEquals(stub.getName, !opaque, stub.isDeclaration)
      assertEquals(if (opaque) Some("caps.CapSet") else None, stub.typeText)
    }
  }

  private def assertOrdinaryAliasBounds(stubs: Seq[ScTypeAliasStub], opaque: Boolean): Unit = {
    for (stub <- stubs) {
      val alias = stub.getPsi
      assertEquals(stub.lowerBoundText, alias.lowerTypeElement.map(_.getText))
      assertEquals(stub.upperBoundText, alias.upperTypeElement.map(_.getText))
      if (opaque) {
        assertEquals(stub.typeText, alias.asInstanceOf[ScTypeAliasDefinition].aliasedTypeElement.map(_.getText))
      }
    }
  }

  private def assertAliasCaptureBounds(aliases: Seq[ScTypeAlias]): Unit = {
    val Seq(lower, upper, both, ordinary) = aliases
    assertEquals(Some("{}"), lower.lowerCaptureSet.map(_.getText))
    assertTrue(lower.upperCaptureSet.isEmpty)
    assertTrue(upper.lowerCaptureSet.isEmpty)
    assertEquals(Some("{any}"), upper.upperCaptureSet.map(_.getText))
    assertEquals(Some("{}"), both.lowerCaptureSet.map(_.getText))
    assertEquals(Some("{any}"), both.upperCaptureSet.map(_.getText))
    assertTrue(ordinary.lowerCaptureSet.isEmpty)
    assertTrue(ordinary.upperCaptureSet.isEmpty)
  }

  def testParameterCaptureBoundsAfterStubSerializationAndAstLoading(): Unit = {
    val virtualFile = myFixture.getTempDirFixture.createFile("Box.scala",
      "class Box[C^ >: {} <: caps.CapSet, D^ <: {any.except[Control]}, T <: AnyRef]")
    val file = PsiManager.getInstance(getProject).findFile(virtualFile).asInstanceOf[PsiFileImpl]
    assertFalse("Start from stub-backed source PSI", file.isContentsLoaded)

    val parameters = AstLoadingFilter.disallowTreeLoading[Seq[ScTypeParamStub], Exception] { () =>
      val tree = file.getStubTree
      assertNotNull(tree)
      val stubs = tree.getPlainList.asScala.toSeq.filterByType[ScTypeParamStub]
      assertStoredParameterBounds(stubs)

      val restored = roundTripStubTree(tree)
      assertStoredParameterBounds(restored.getPlainList.asScala.toSeq.filterByType[ScTypeParamStub])

      assertOrdinaryParameterBounds(stubs.map(_.getPsi))
      stubs
    }
    assertFalse("Ordinary bound APIs must not load capture syntax", file.isContentsLoaded)

    // Capture syntax access is deliberately AST-backed; ordinary stub fields stay unchanged.
    val parameterPsi = parameters.map(_.getPsi)
    assertParameterCaptureBounds(parameterPsi)
    assertTrue(file.isContentsLoaded)
    assertTrue(file.depthFirst().filterByType[PsiErrorElement].isEmpty)

    assertOrdinaryParameterBounds(parameterPsi)
    assertStoredParameterBounds(parameters)
  }

  private def assertParameterCaptureBounds(parameters: Seq[ScTypeParam]): Unit = {
    val Seq(c, d, t) = parameters
    assertTrue(c.isCaptureSetParameter)
    assertTrue(d.isCaptureSetParameter)
    assertFalse(t.isCaptureSetParameter)
    assertEquals(Some("{}"), c.lowerCaptureSet.map(_.getText))
    assertEquals(Some("{any.except[Control]}"), d.upperCaptureSet.map(_.getText))
    assertTrue(t.upperCaptureSet.isEmpty)
  }

  private def assertOrdinaryParameterBounds(parameters: Seq[ScTypeParam]): Unit = {
    val Seq(c, d, t) = parameters
    assertEquals(Seq("C", "D", "T"), parameters.map(_.name))
    assertTrue(c.lowerTypeElement.isEmpty)
    assertEquals(Some("caps.CapSet"), c.upperTypeElement.map(_.getText))
    assertTrue(d.lowerTypeElement.isEmpty)
    assertTrue(d.upperTypeElement.isEmpty)
    assertEquals(Some("AnyRef"), t.upperTypeElement.map(_.getText))
  }

  private def assertStoredParameterBounds(parameters: Seq[ScTypeParamStub]): Unit = {
    assertEquals(Seq("C", "D", "T"), parameters.map(_.getName))
    val Seq(c, d, t) = parameters
    assertEquals("C^ >: {} <: caps.CapSet", c.text)
    assertEquals(None, c.lowerBoundText)
    assertEquals(Some("caps.CapSet"), c.upperBoundText)
    assertEquals("D^ <: {any.except[Control]}", d.text)
    assertEquals(None, d.lowerBoundText)
    assertEquals(None, d.upperBoundText)
    assertEquals(Some("AnyRef"), t.upperBoundText)
  }
}
