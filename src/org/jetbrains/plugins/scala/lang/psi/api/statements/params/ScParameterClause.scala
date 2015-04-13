package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
  * @author Alexander Podkhalyuzin
  * Date: 21.03.2008
  */
trait ScParameterClause extends ScalaPsiElement {

  def parameters: Seq[ScParameter]

  def effectiveParameters: Seq[ScParameter]

  //hack: no ClassParamList present at the moment
  def unsafeClassParameters = effectiveParameters.asInstanceOf[Seq[ScClassParameter]]

  def paramTypes: Seq[ScType] = parameters.map(_.getType(TypingContext.empty).getOrAny)

  def isImplicit: Boolean

  def implicitToken: Option[PsiElement] = Option(findFirstChildByType(ScalaTokenTypes.kIMPLICIT))

  def hasRepeatedParam: Boolean = parameters.length > 0 && parameters.apply(parameters.length - 1).isRepeatedParameter

  def getSmartParameters: Seq[Parameter] = {
    effectiveParameters.map { param =>
        new Parameter(param.name, param.deprecatedName, param.getType(TypingContext.empty).getOrNothing,
          param.getType(TypingContext.empty).getOrNothing, param.isDefaultParam, param.isRepeatedParameter,
          param.isCallByNameParameter, param.index, Some(param))
    }
  }

  /**
    * add parameter as last parameter in clause
    * if clause has repeated parameter, add before this parameter.
    */
  def addParameter(param: ScParameter): ScParameterClause
}