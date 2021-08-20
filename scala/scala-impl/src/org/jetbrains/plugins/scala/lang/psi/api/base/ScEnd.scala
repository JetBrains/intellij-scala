package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.{PsiElement, PsiReference}

trait ScEnd extends ScalaPsiElement with PsiReference {
  /**
   * @return the token that designates which element is ended by this end-element
   */
  def endingElementDesignator: PsiElement
}
