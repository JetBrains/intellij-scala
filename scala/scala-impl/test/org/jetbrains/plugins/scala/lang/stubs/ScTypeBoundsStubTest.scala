package org.jetbrains.plugins.scala.lang.stubs

import com.intellij.psi.stubs.StubTree
import com.intellij.util.AstLoadingFilter
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScTypeAliasStub, ScTypeParamStub}
import org.jetbrains.plugins.scala.lang.stubs.StubSerializationTestUtil.roundTripStubTree
import org.jetbrains.plugins.scala.project.{ScalaFeaturePusher, ScalaFeatures}
import org.junit.Assert._

/**
 * Protects ordinary parameter/member bound metadata through binary stub serialization.
 * Checks variance, higher-kinded and context-bound metadata and stub-backed PSI access
 * while forbidding source AST loading; capture-bound lifecycle checks have a separate suite.
 */
class ScTypeBoundsStubTest extends SimpleTestCase {
  def testOrdinaryBoundsSurviveSerializationWithoutLoadingSourceAst(): Unit = {
    val source =
      """trait Bounds[+A >: Nothing <: AnyRef, -B, F[_]] {
        |  type Member >: Nothing <: AnyRef
        |  def ordered[T <: AnyRef: Ordering]: T
        |}
        |""".stripMargin
    val virtualFile = fixture.getTempDirFixture.createFile("Bounds.scala", source)
    ScalaFeaturePusher.setFeatures(virtualFile, ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_2_13))
    val context = s"ordinary bound lifecycle in `${virtualFile.getPath}`"
    val file = StubTreeTestUtils.findUnloadedSourceFile(fixture.getProject, virtualFile)

    AstLoadingFilter.disallowTreeLoading[Exception] { () =>
      val tree = file.getStubTree
      assertNotNull(s"$context: source stub creation must return a tree before AST loading", tree)
      assertStoredBounds(tree, s"$context: source stubs before AST loading")
      assertStubBackedPsiBounds(tree, s"$context: ordinary bound PSI before AST loading")

      val restored = roundTripStubTree(tree)
      assertStoredBounds(restored, s"$context: deserialized stubs")
    }
    val sourceAstLoadedAfterSerialization = file.isContentsLoaded
    assertFalse(
      s"$context: bound access and serialization must keep the source AST unloaded",
      sourceAstLoadedAfterSerialization
    )
  }

  private def assertStoredBounds(tree: StubTree, context: String): Unit = {
    val parameters = StubTreeTestUtils.stubsOfType[ScTypeParamStub](tree, context)
    assertStoredParameterBounds(parameters)

    val Seq(member) = StubTreeTestUtils.stubsOfType[ScTypeAliasStub](tree, context)
    assertStoredAliasBounds(member)
  }

  private def assertStoredParameterBounds(parameters: Seq[ScTypeParamStub]): Unit = {
    assertEquals(Seq("A", "B", "F", "_", "T"), parameters.map(_.getName))
    val Seq(a, b, f, _, t) = parameters

    // Variance and ordinary bounds.
    assertEquals("+A >: Nothing <: AnyRef", a.text)
    assertEquals(Some("Nothing"), a.lowerBoundText)
    assertEquals(Some("AnyRef"), a.upperBoundText)
    assertTrue(a.isCovariant)
    assertFalse(a.isContravariant)

    assertTrue(b.isContravariant)
    assertFalse(b.isCovariant)
    assertEquals(None, b.lowerBoundText)
    assertEquals(None, b.upperBoundText)

    // Higher-kinded parameters and context bounds.
    assertEquals("F[_]", f.text)
    assertEquals(None, t.lowerBoundText)
    assertEquals(Some("AnyRef"), t.upperBoundText)
    assertEquals(Seq("Ordering"), t.contextBoundsTexts.toSeq)

    assertTrue(parameters.forall(_.containingFileName == "Bounds.scala"))
  }

  private def assertStoredAliasBounds(member: ScTypeAliasStub): Unit = {
    assertTrue(member.isDeclaration)
    assertEquals(Some("Nothing"), member.lowerBoundText)
    assertEquals(Some("AnyRef"), member.upperBoundText)
  }

  private def assertStubBackedPsiBounds(tree: StubTree, context: String): Unit = {
    val Seq(a, _, _, _, t) = StubTreeTestUtils.stubsOfType[ScTypeParamStub](tree, context)
    val Seq(member) = StubTreeTestUtils.stubsOfType[ScTypeAliasStub](tree, context)

    assertEquals("A", a.getPsi.name)
    assertTrue(a.getPsi.isCovariant)
    assertEquals(Some("Nothing"), a.getPsi.lowerTypeElement.map(_.getText))
    assertEquals(Some("AnyRef"), a.getPsi.upperTypeElement.map(_.getText))
    assertEquals(Seq("Ordering"), t.getPsi.contextBounds.map(_.typeElement.getText))
    assertEquals(Some("Nothing"), member.getPsi.lowerTypeElement.map(_.getText))
    assertEquals(Some("AnyRef"), member.getPsi.upperTypeElement.map(_.getText))
  }
}
