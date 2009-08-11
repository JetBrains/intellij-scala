package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScPrimaryConstructor extends ScMember {
  /**
   *  @return has annotation
   */
  def hasAnnotation: Boolean

  /**
   *  @return has access modifier
   */
  def hasModifier: Boolean
  def getClassNameText: String

  def parameterList: ScParameters

  //hack: no ClassParamList present at the moment
  def parameters : Seq[ScClassParameter] = parameterList.params.asInstanceOf[Seq[ScClassParameter]]

  /**
   * return only parameters, which are additionally members.
   */
  def valueParameters: Seq[ScClassParameter] = parameters.filter((p: ScClassParameter) => p.isVal || p.isVar)
}