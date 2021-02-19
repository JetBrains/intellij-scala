package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement

trait ScEndBase extends ScalaPsiElementBase { this: ScEnd =>
  /**
   * @return the token that designates which element is ended by this end-element
   */
  def endingElementDesignator: PsiElement
}