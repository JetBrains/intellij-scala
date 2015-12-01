package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPathElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
* @author Alexander Podkhalyuzin
* Date: 14.03.2008
*/

trait ScSuperReference extends ScExpression with ScPathElement {
  /**
   * @return is reference in decompiled file from Self type class
   */
  def isHardCoded: Boolean

  //type of M for super[M]
  def staticSuper : Option[ScType]
  
  //name of super type as written in code
  def staticSuperName: String

  //for A.super or simply super
  def drvTemplate : Option[ScTemplateDefinition]

  def reference: Option[ScStableCodeReferenceElement] = findChild(classOf[ScStableCodeReferenceElement])
}