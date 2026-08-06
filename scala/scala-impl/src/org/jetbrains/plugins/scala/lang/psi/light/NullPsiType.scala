package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiAnnotation, PsiType, PsiTypeVisitor}

//temporary placeholder for light method return type or parameter type of ScLightParameter
private[light] object NullPsiType extends PsiType(PsiAnnotation.EMPTY_ARRAY) {

  override def getPresentableText(boolean: Boolean): String = ???

  override def getPresentableText: String = ???

  override def getCanonicalText: String = ???

  override def isValid: Boolean = ???

  override def equalsToText(text: String): Boolean = ???

  override def accept[A](visitor: PsiTypeVisitor[A]): A = ???

  override def getResolveScope: GlobalSearchScope = ???

  override def getSuperTypes: Array[PsiType] = ???
}
