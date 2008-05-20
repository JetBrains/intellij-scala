package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 14.04.2008
*/

trait ScTypeElement extends ScalaPsiElement with PsiTypeElement{

  def getInnermostComponentReferenceElement = null

  //Stub for java compatibility
  def getType = Bottom.BOTTOM

}