package org.jetbrains.plugins.scala.lang.psi.api.expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import base.{ScStableCodeReferenceElement, ScPathElement}
import com.intellij.psi.PsiClass
import psi.ScalaPsiElement
import toplevel.typedef.ScTypeDefinition

/** 
* @author Alexander Podkhalyuzin
* Date: 14.03.2008
*/

trait ScSuperReference extends ScExpression with ScPathElement {
  //type of M for super[M]
  def staticSuper : Option[ScType]

  //for A.super or simply super
  def drvClass : Option[ScTypeDefinition]

  def qualifier = findChild(classOf[ScStableCodeReferenceElement])
}