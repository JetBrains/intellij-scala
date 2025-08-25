package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.{PsiDirectory, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.annotation.tailrec

trait Context {
  def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean
}

object Context {
  def apply(place: PsiElement): Context = new Context() {
    override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = {
      if (!opaqueTypeAlias.isOpaque)
        throw new IllegalArgumentException("Opaque type alias expected")

      val p: PsiElement => Boolean = {
        case td: ScTypeDefinition => !td.isTopLevel || td.baseCompanion.contains(opaqueTypeAlias)
        case _: PsiDirectory => false
        case _ => true
      }

      containingFileOf(opaqueTypeAlias).getOriginalFile == containingFileOf(place).getOriginalFile &&
        place.contexts.takeWhile(p).contains(opaqueTypeAlias.getContext)
    }

    override def toString: String = place.toString
  }

  @tailrec
  private def containingFileOf(e: PsiElement): PsiFile = {
    val file = e.getContainingFile
    if (file == null) return null
    val fileContext = file.getContext
    if (fileContext == null) file else containingFileOf(fileContext)
  }

  object Empty extends Context {
    override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = true

    override def toString: String = "<empty>"
  }

//  @deprecated("Provide Context(element) or use EmptyContext")
  implicit val Default: Context = new Context {
    override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = true

    override def toString: String = "<default>"
  }
}
