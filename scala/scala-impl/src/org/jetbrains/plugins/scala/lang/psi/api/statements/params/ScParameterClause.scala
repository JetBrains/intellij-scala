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
  def unsafeClassParameters: Seq[ScClassParameter] = effectiveParameters.asInstanceOf[Seq[ScClassParameter]]

  def paramTypes: Seq[ScType] = parameters.map(_.getType(TypingContext.empty).getOrAny)

  def isImplicit: Boolean

  def implicitToken: Option[PsiElement] = Option(findFirstChildByType(ScalaTokenTypes.kIMPLICIT))

  def hasRepeatedParam: Boolean = parameters.lastOption.exists(_.isRepeatedParameter)

  def getSmartParameters: Seq[Parameter] = effectiveParameters.map(Parameter(_))

  /**
    * add parameter as last parameter in clause
    * if clause has repeated parameter, add before this parameter.
    */
  def addParameter(param: ScParameter): ScParameterClause

  def owner: PsiElement
}

object ScParameterClause {
  def unapplySeq(e: ScParameterClause): Some[Seq[ScParameter]] = Some(e.parameters)
}