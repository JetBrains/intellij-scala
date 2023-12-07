package org.jetbrains.plugins.scala.lang.psi.fake

import com.intellij.psi.{PsiAnnotation, PsiType, PsiTypeVisitor}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.ScTypePresentationExt

private[lang] class FakePsiType(val tp: ScType) extends PsiType(PsiAnnotation.EMPTY_ARRAY) {

  override def getPresentableText(boolean: Boolean): String = getPresentableText

  override def getPresentableText: String = tp.codeText(TypePresentationContext.emptyContext)

  override def getCanonicalText: String = tp.canonicalCodeText

  override def isValid: Boolean = true

  override def equalsToText(text: String): Boolean = false

  override def accept[A](visitor: PsiTypeVisitor[A]): A = visitor.visitType(this)

  override def getResolveScope: GlobalSearchScope = null

  override def getSuperTypes: Array[PsiType] = Array.empty
}
