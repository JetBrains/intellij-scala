package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScPsiDocToken

class ScPsiDocTokenImpl(
  override val tokenType: ScalaDocElementType,
  text: CharSequence
) extends LeafPsiElement(tokenType, text) with ScPsiDocToken {

  override def toString: String = s"ScPsiDocToken(${tokenType.toString})";

  override def getReferences: Array[PsiReference] =
    ReferenceProvidersRegistry.getReferencesFromProviders(this)
}
