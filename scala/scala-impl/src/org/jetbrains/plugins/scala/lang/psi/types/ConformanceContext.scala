package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.{PsiDirectory, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.PsiElementContext
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.annotation.tailrec

/**
 * Context of a conformance operation - location in code in abstract representation. Abstracts over a concrete [[PsiElement]], which might also be absent.
 *
 * Currently, it's a location relative to opaque type aliases. However, the name is intentionally generic to subsequently unify more context parameters.
 *
 * Many operations are context-dependent (e.g. whether one type conforms to another type, whether to show the right-hard side of a type alias in Quick Documentation, etc.).
 *
 * Thus, it's important to always provide a proper context.
 *
 * @see [[com.intellij.psi.PsiElement]]
 * @see [[org.jetbrains.plugins.scala.lang.psi.ElementScope]]
 * @see [[org.jetbrains.plugins.scala.project.ProjectContext]]
 * @see [[org.jetbrains.plugins.scala.project.ScalaFeatures]]
 * @see [[org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext]]
 */
trait ConformanceContext {
  def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean
}

object ConformanceContext {
  def apply(place: PsiElement): ConformanceContext = PsiElementContext(place)

  trait PsiBasedImpl extends ConformanceContext with PsiElementContext {
    override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = {
      if (!opaqueTypeAlias.isOpaque)
        throw new IllegalArgumentException("Opaque type alias expected")

      val p: PsiElement => Boolean = {
        case td: ScTypeDefinition => !td.isTopLevel || td.baseCompanion.contains(opaqueTypeAlias)
        case _: PsiDirectory => false
        case _ => true
      }

      containingFileOf(opaqueTypeAlias).getOriginalFile == containingFileOf(psiElement).getOriginalFile &&
        psiElement.contexts.takeWhile(p).contains(opaqueTypeAlias.getContext)
    }

    override def toString: String = s"ConformanceContext(${psiElement.toString})"
  }

  @tailrec
  private def containingFileOf(e: PsiElement): PsiFile = {
    val file = e.getContainingFile
    if (file == null) return null
    val fileContext = file.getContext
    if (fileContext == null) file else containingFileOf(fileContext)
  }

  /**
   * An empty context, not associated with a particular location in code.
   *
   * Can be used, instead of the [[Default]] context, where a context doesn't matter (which should almost never be the case).
   *
   * Currently, represents a context where all opaque type aliases are effectively transparent (to reproduce how opaque type aliases were handled before).
   *
   * The name is intentionally generic because subsequently [[ConformanceContext]] may unify more context parameters.
   *
   * TODO Use dedicated Transparent and Opaque contexts for opaque type aliases in the future
   */
  trait Empty extends ConformanceContext {
    override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = true

    override def toString: String = "<empty>"
  }
  object Empty extends Empty

  /**
   * Default argument for [[ConformanceContext]] parameters, to maintain source compatibility.
   *
   * Functionally, it's equivalent to the [[Empty]] context, but differs statically and dynamically to distinguish the intention.
   *
   * It's good to always provide a proper context.
   *
   * However, there are many places where context is not given or propagated, including in third-party code (which might or might no be not a problem in practice).
   *
   * The value is not deprecated because compilation warnings are treated as errors.
   *
   * TODO Remove the default argument in the future
   */
//  @deprecated("Provide Context(element) or use EmptyContext")
  implicit val Default: ConformanceContext = new ConformanceContext {
    override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = true

    override def toString: String = "<default>"
  }
}
