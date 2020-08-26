package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt

trait ScDeclaredElementsHolder extends ScalaPsiElement {
  def declaredElements : collection.Seq[PsiNamedElement]

  def declaredNames: collection.Seq[String] = declaredElements.map(_.name)

  /**
   * @return array for Java compatibility [[org.jetbrains.plugins.scala.gotoclass.ScalaGoToSymbolContributor]]
   */
  def declaredElementsArray : Array[PsiNamedElement] = declaredElements.toArray
}