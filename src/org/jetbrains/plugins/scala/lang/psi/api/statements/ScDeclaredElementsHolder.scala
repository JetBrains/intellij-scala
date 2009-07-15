package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiNamedElement

trait ScDeclaredElementsHolder extends ScalaPsiElement {
  def declaredElements : Seq[PsiNamedElement]

  /**
   * @return array for Java compatibility {@link org.jetbrains.plugins.scala.gotoclass.ScalaGoToSymbolContributor} 
   */
  def declaredElementsArray : Array[PsiNamedElement] = declaredElements.toArray
}