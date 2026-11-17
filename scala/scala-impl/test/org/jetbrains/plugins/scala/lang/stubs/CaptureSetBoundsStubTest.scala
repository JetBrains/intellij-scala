package org.jetbrains.plugins.scala.lang.stubs

import com.intellij.psi.PsiErrorElement
import com.intellij.util.AstLoadingFilter
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, ScalaLightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.TypeAliasMemberKind
import org.jetbrains.plugins.scala.lang.psi.TypeBoundsTestAssertions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScTypeAliasStub, ScTypeParamStub}
import org.jetbrains.plugins.scala.lang.stubs.StubSerializationTestUtil.roundTripStubTree
import org.junit.Assert._

/**
 * Tests capture-bound ownership through source stub creation, serialization and AST loading.
 * Ordinary type-bound APIs must keep capture syntax out of stored type text and avoid
 * loading the AST; capture accessors deliberately load it and return the correct children.
 * Covers parameters and abstract/opaque aliases, without capture-set type semantics.
 */
class CaptureSetBoundsStubTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_8

  override protected def additionalCompilerOptions: Seq[String] = CaptureCheckingTestUtils.compilerOptions

  def testAbstractAliasCaptureBoundsSurviveStubLifecycle(): Unit =
    assertAliasCaptureBounds(TypeAliasMemberKind.AbstractTypeDeclaration)

  def testOpaqueAliasCaptureBoundsSurviveStubLifecycle(): Unit =
    assertAliasCaptureBounds(TypeAliasMemberKind.OpaqueTypeAliasDefinition)

  private def assertAliasCaptureBounds(memberKind: TypeAliasMemberKind): Unit = {
    val prefix = memberKind.sourcePrefix
    val rhs = memberKind.definitionSuffix("caps.CapSet")
    val virtualFile = myFixture.getTempDirFixture.createFile("Box.scala",
      s"""trait Box {
         |  $prefix Lower >: {} <: caps.CapSet$rhs
         |  $prefix Upper >: caps.CapSet <: {any}$rhs
         |  $prefix Both >: {} <: {any}$rhs
         |  $prefix Ordinary >: Nothing <: caps.CapSet$rhs
         |}
         |""".stripMargin)
    val context = s"${memberKind.description} capture-bound lifecycle in `${virtualFile.getPath}`"
    val file = StubTreeTestUtils.findUnloadedSourceFile(getProject, virtualFile)

    val stubs = AstLoadingFilter.disallowTreeLoading[Seq[ScTypeAliasStub], Exception] { () =>
      val tree = file.getStubTree
      assertNotNull(s"$context: source stub creation must return a tree before AST loading", tree)
      val aliases = StubTreeTestUtils.stubsOfType[ScTypeAliasStub](
        tree,
        s"$context: source stubs before AST loading"
      )
      assertStoredAliasBounds(aliases, memberKind, "source stubs before AST loading")
      assertOrdinaryAliasBounds(aliases, memberKind, "before source AST loading")

      val restored = roundTripStubTree(tree)
      val restoredAliases = StubTreeTestUtils.stubsOfType[ScTypeAliasStub](
        restored,
        s"$context: deserialized stubs"
      )
      assertStoredAliasBounds(restoredAliases, memberKind, "deserialized stubs")
      aliases
    }
    val sourceAstLoadedAfterSerialization = file.isContentsLoaded
    assertFalse(
      s"$context: ordinary bound access and serialization must keep the source AST unloaded",
      sourceAstLoadedAfterSerialization
    )

    // Capture syntax intentionally loads the AST; the same alias PSI must then retain both bound kinds.
    assertAliasCaptureBounds(stubs.map(_.getPsi), memberKind)
    assertTrue(s"$context: capture-bound access must load the source AST", file.isContentsLoaded)
    val parserErrors = file.depthFirst().filterByType[PsiErrorElement]
      .map(error => (error.getTextOffset, error.getErrorDescription)).toSeq
    assertEquals(
      s"$context: unexpected parser errors after AST loading (offset, description)",
      Seq.empty[(Int, String)],
      parserErrors
    )

    assertOrdinaryAliasBounds(stubs, memberKind, "after capture access loaded the source AST")
    assertStoredAliasBounds(stubs, memberKind, "source stubs after AST loading")
  }

  private def assertStoredAliasBounds(
    stubs: Seq[ScTypeAliasStub],
    memberKind: TypeAliasMemberKind,
    lifecyclePhase: String
  ): Unit = {
    val context = s"${memberKind.description}, $lifecyclePhase"
    assertEquals(
      s"$context: alias names and source order",
      Seq("Lower", "Upper", "Both", "Ordinary"),
      stubs.map(_.getName)
    )
    assertEquals(
      s"$context: ordinary lower bound texts in alias order",
      Seq(None, Some("caps.CapSet"), None, Some("Nothing")),
      stubs.map(_.lowerBoundText)
    )
    assertEquals(
      s"$context: ordinary upper bound texts in alias order",
      Seq(Some("caps.CapSet"), None, None, Some("caps.CapSet")),
      stubs.map(_.upperBoundText)
    )

    for (stub <- stubs) {
      assertStoredAliasKind(stub, memberKind, lifecyclePhase)
    }
  }

  private def assertStoredAliasKind(
    stub: ScTypeAliasStub,
    memberKind: TypeAliasMemberKind,
    lifecyclePhase: String
  ): Unit = {
    val context = s"${memberKind.description} `${stub.getName}`, $lifecyclePhase"
    val expectedIsDeclaration = memberKind == TypeAliasMemberKind.AbstractTypeDeclaration
    assertEquals(s"$context: declaration flag", expectedIsDeclaration, stub.isDeclaration)
    assertEquals(
      s"$context: stored defining type text",
      memberKind.expectedAliasedTypeText("caps.CapSet"),
      stub.typeText
    )
  }

  /**
   * Checks ordinary bounds against their stubs for one alias kind.
   *
   * @param memberKind expected abstract declaration or opaque alias definition;
   *                   the latter also checks its defining type against the stub.
   * @param lifecyclePhase identifies access before or after capture access loads the AST
   */
  private def assertOrdinaryAliasBounds(
    stubs: Seq[ScTypeAliasStub],
    memberKind: TypeAliasMemberKind,
    lifecyclePhase: String
  ): Unit = {
    for (stub <- stubs) {
      assertOrdinaryBoundsForAlias(stub, memberKind, lifecyclePhase)
    }
  }

  /** Checks one alias's ordinary bounds and the RHS of an expected opaque alias. */
  private def assertOrdinaryBoundsForAlias(
    stub: ScTypeAliasStub,
    memberKind: TypeAliasMemberKind,
    lifecyclePhase: String
  ): Unit = {
    val alias = stub.getPsi
    val aliasDescription = s"${memberKind.description} `${stub.getName}`, $lifecyclePhase"
    assertNotNull(s"$aliasDescription: expected PSI for the source stub", alias)
    assertBoundElementTexts(
      s"ordinary type bounds of $aliasDescription",
      alias.lowerTypeElement,
      alias.upperTypeElement,
      stub.lowerBoundText,
      stub.upperBoundText
    )

    memberKind match {
      case TypeAliasMemberKind.AbstractTypeDeclaration => ()
      case TypeAliasMemberKind.OpaqueTypeAliasDefinition =>
        assertTrue(
          s"$aliasDescription: expected ScTypeAliasDefinition, actual ${alias.getClass.getSimpleName}",
          alias.isInstanceOf[ScTypeAliasDefinition]
        )
        assertEquals(
          s"$aliasDescription: aliased type text must match the stub",
          stub.typeText,
          alias.asInstanceOf[ScTypeAliasDefinition].aliasedTypeElement.map(_.getText)
        )
    }
  }

  private def assertAliasCaptureBounds(
    aliases: Seq[ScTypeAlias],
    memberKind: TypeAliasMemberKind
  ): Unit = {
    val context = s"capture bounds of ${memberKind.description} members after source AST loading"
    assertEquals(
      s"$context: alias names and source order",
      Seq("Lower", "Upper", "Both", "Ordinary"),
      aliases.map(_.name)
    )
    val Seq(lower, upper, both, ordinary) = aliases

    assertCaptureBoundsForAlias(lower, memberKind, Some("{}"), None)
    assertCaptureBoundsForAlias(upper, memberKind, None, Some("{any}"))
    assertCaptureBoundsForAlias(both, memberKind, Some("{}"), Some("{any}"))
    assertCaptureBoundsForAlias(ordinary, memberKind, None, None)
  }

  private def assertCaptureBoundsForAlias(
    alias: ScTypeAlias,
    memberKind: TypeAliasMemberKind,
    expectedLowerText: Option[String],
    expectedUpperText: Option[String]
  ): Unit = {
    assertBoundElementTexts(
      s"capture bounds of ${memberKind.description} `${alias.name}` after source AST loading",
      alias.lowerCaptureSet,
      alias.upperCaptureSet,
      expectedLowerText,
      expectedUpperText
    )
  }

  def testParameterCaptureBoundsAfterStubSerializationAndAstLoading(): Unit = {
    val virtualFile = myFixture.getTempDirFixture.createFile("Box.scala",
      "class Box[C^ >: {} <: caps.CapSet, D^ <: {any.except[Control]}, T <: AnyRef]")
    val context = s"parameter capture-bound lifecycle in `${virtualFile.getPath}`"
    val file = StubTreeTestUtils.findUnloadedSourceFile(getProject, virtualFile)

    val parameters = AstLoadingFilter.disallowTreeLoading[Seq[ScTypeParamStub], Exception] { () =>
      val tree = file.getStubTree
      assertNotNull(s"$context: source stub creation must return a tree before AST loading", tree)
      val stubs = StubTreeTestUtils.stubsOfType[ScTypeParamStub](
        tree,
        s"$context: source stubs before AST loading"
      )
      assertStoredParameterBounds(stubs)

      val restored = roundTripStubTree(tree)
      val restoredParameters = StubTreeTestUtils.stubsOfType[ScTypeParamStub](
        restored,
        s"$context: deserialized stubs"
      )
      assertStoredParameterBounds(restoredParameters)

      val parameterPsi = stubs.map(_.getPsi)
      assertOrdinaryParameterBounds(parameterPsi)
      stubs
    }
    val sourceAstLoadedAfterSerialization = file.isContentsLoaded
    assertFalse(
      s"$context: ordinary bound access and serialization must keep the source AST unloaded",
      sourceAstLoadedAfterSerialization
    )

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
