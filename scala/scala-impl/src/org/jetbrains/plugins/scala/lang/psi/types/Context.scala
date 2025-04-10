package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition

trait Context {
  def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean
}

object Context {
  def apply(place: PsiElement): Context = new Context() {
    override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = {
      if (!opaqueTypeAlias.isOpaque)
        throw new IllegalArgumentException("Opaque type alias expected")

      opaqueTypeAlias.getContainingFile == place.getContainingFile &&
        place.parentsInFile.contains(opaqueTypeAlias.getParent)
    }
  }

  implicit object Empty extends Context {
    override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = true
  }
}
