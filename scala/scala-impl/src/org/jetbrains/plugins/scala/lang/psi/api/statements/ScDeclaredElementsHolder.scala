package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt

trait ScDeclaredElementsHolderBase extends ScalaPsiElementBase { this: ScDeclaredElementsHolder =>
  def declaredElements : Seq[PsiNamedElement]

  def declaredNames: Seq[String] = declaredElements.map(_.name)

  /**
   * @return array for Java compatibility [[org.jetbrains.plugins.scala.gotoclass.ScalaGoToSymbolContributor]]
   */
  def declaredElementsArray : Array[PsiNamedElement] = declaredElements.toArray
}