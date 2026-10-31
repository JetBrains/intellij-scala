package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.PsiManagerEx
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScProjectionType, ScThisType}
import org.junit.Assert._

/**
 * A file whose PSI is reloaded in place (`AbstractFileViewProvider.onContentReload`, which IntelliJ does when a file
 * without a Document changes on disk) gets new elements, and the old ones become invalid. The reload fires
 * `childrenChanged` for the file but does not call `subtreeChanged`, so `ModTracker.anyScalaPsiChange` is not
 * incremented. A cache keyed on that tracker whose key mentions only elements of another file, but whose value
 * mentions elements of the reloaded file, then hands out invalid elements.
 */
class CanonicalizeTargetReloadTest extends ScalaLightCodeInsightFixtureTestCase {

  private val ctxText =
    """package a
      |trait G { class Sym }
      |trait Ctx {
      |  val universe: G
      |  val global: universe.type = universe
      |}
      |""".stripMargin

  private val internalsText =
    """package b
      |import a._
      |trait Api { def sym: Any }
      |trait Internals { self: Ctx =>
      |  val g: global.type = global
      |  def sym: g.Sym = ???
      |  lazy val internal: Api = new Api { def sym: g.Sym = Internals.this.sym }
      |  val x = sym
      |}
      |""".stripMargin

  private def invalidElements(tp: ScType): Seq[PsiNamedElement] = {
    val buf = Seq.newBuilder[PsiNamedElement]
    tp.visitRecursively {
      case d: DesignatorOwner if !d.element.isValid => buf += d.element
      case _                                        =>
    }
    buf.result()
  }

  private def reloadInPlace(file: ScalaFile): Unit =
    WriteAction.run[RuntimeException] { () =>
      PsiManagerEx.getInstanceEx(getProject).getFileManagerEx.reloadPsiAfterTextChange(file.getViewProvider, file.getVirtualFile)
    }

  private def typeEverything(file: ScalaFile): Seq[ScType] =
    file.depthFirst().toSeq.flatMap {
      case e: ScExpression   => e.`type`().toOption
      case te: ScTypeElement => te.`type`().toOption
      case _                 => None
    }

  def testCanonicalizedPathAfterInPlaceReload(): Unit = {
    val ctx = myFixture.addFileToProject("a/Ctx.scala", ctxText).asInstanceOf[ScalaFile]
    val internalsFile = myFixture.addFileToProject("b/Internals.scala", internalsText).asInstanceOf[ScalaFile]
    val internals = internalsFile.depthFirst().collectFirst { case t: ScTemplateDefinition if t.name == "Internals" => t }.get
    val g = internalsFile.depthFirst().collectFirst { case p: ScBindingPattern if p.name == "g" => p }.get

    // Only elements of Internals.scala in the key; the canonical path ends in Ctx.scala's `universe`.
    val key = ScProjectionType(ScThisType(internals), g)
    val before = ThisTypeSubstitution.canonicalizeTarget(key).toString
    assertTrue(s"expected a path to universe, got $before", before.contains("universe"))

    val oldUniverse = ctx.depthFirst().collectFirst { case p: ScTypedDefinition if p.name == "universe" => p }.get
    reloadInPlace(ctx)
    assertFalse("the reload should invalidate Ctx.scala's elements", oldUniverse.isValid)

    val after = ThisTypeSubstitution.canonicalizeTarget(key)
    assertEquals("canonicalizeTarget hands out elements of the reloaded file", Seq.empty, invalidElements(after).map(_.getClass.getSimpleName))
    assertEquals(before, after.toString)

    val stale = typeEverything(internalsFile).flatMap(invalidElements)
    assertEquals("types in Internals.scala hold invalid elements", Seq.empty, stale.map(_.getClass.getSimpleName))
  }
}
