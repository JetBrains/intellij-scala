package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScPrimaryConstructor extends ScalaPsiElement {
  /**
   *  Returns does constructor have annotation
   *
   *  @return has annotation
   */
  def hasAnnotation: Boolean

  /**
   *  Returns does constructor have access modifier
   *
   *  @return has access modifier
   */
  def hasModifier: Boolean
}