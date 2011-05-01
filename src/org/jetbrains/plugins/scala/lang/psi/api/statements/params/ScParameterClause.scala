package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import types._
import nonvalue.Parameter
import types.result.TypingContext
import lexer.ScalaTokenTypes
import com.intellij.psi.PsiElement

/**
  * @author Alexander Podkhalyuzin
  * Date: 21.03.2008
  */
trait ScParameterClause extends ScalaPsiElement {

  def parameters: Seq[ScParameter]

  //hack: no ClassParamList present at the moment
  def unsafeClassParameters = parameters.asInstanceOf[Seq[ScClassParameter]]

  def paramTypes: Seq[ScType] = parameters.map(_.getType(TypingContext.empty).getOrElse(Any))

  def isImplicit: Boolean

  def implicitToken: Option[PsiElement] = Option(findFirstChildByType(ScalaTokenTypes.kIMPLICIT))

  def hasRepeatedParam: Boolean = parameters.length > 0 && parameters.apply(parameters.length - 1).isRepeatedParameter

  def getSmartParameters: Seq[Parameter] = {
    parameters.map(param =>
      Parameter(param.name, param.getType(TypingContext.empty).getOrElse(Nothing), param.isDefaultParam, param.isRepeatedParameter, param.isCallByNameParameter))
  }

  /**
    * add parameter as last parameter in clause
    * if clause has repeated parameter, add before this parameter.
    */
  def addParameter(param: ScParameter): ScParameterClause
}