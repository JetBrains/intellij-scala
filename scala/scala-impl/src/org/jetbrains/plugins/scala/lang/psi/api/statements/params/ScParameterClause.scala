package org.jetbrains.plugins.scala.lang.psi.api.statements
package params

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result._

import scala.annotation.nowarn

trait ScParameterClause extends ScalaPsiElement {

  def parameters: Seq[ScParameter]

  def effectiveParameters: Seq[ScParameter]

  def hasParenthesis: Boolean

  //hack: no ClassParamList present at the moment
  def unsafeClassParameters: Seq[ScClassParameter] = effectiveParameters.asInstanceOf[Seq[ScClassParameter]]

  def paramTypes: Seq[ScType] = parameters.map(_.`type`().getOrAny)

  @deprecated("Use hasImplicitKeyword instead. This will be removed soon.", "2025.1")
  @deprecatedOverriding("Override hasImplicitKeyword instead", "2025.1")
  def isImplicit: Boolean

  @deprecated("Use hasUsingKeyword instead. This will be removed soon.", "2025.1")
  @deprecatedOverriding("Override hasUsingKeyword instead", "2025.1")
  def isUsing: Boolean

  /**
   * Checks whether this parameter clause has an implicit keyword.
   *
   * @note Do not use this to check whether this parameter clause takes contextual arguments.
   *       Use [[isImplicitOrUsing]] instead.
   * @return true if this parameter clause has an implicit keyword
   */
  def hasImplicitKeyword: Boolean = isImplicit: @nowarn("cat=deprecation")

  /**
   * Checks whether this parameter clause has a using keyword.
   *
   * @note Do not use this to check whether this parameter clause takes contextual arguments.
   *       Use [[isImplicitOrUsing]] instead.
   * @return true if this parameter clause has a using keyword
   */
  def hasUsingKeyword: Boolean = isUsing: @nowarn("cat=deprecation")

  /**
   * Whether the parameters of this clause take contextual arguments.
   * It's the case if the clause is either marked with using or implicit.
   */
  def isImplicitOrUsing: Boolean = hasImplicitKeyword || hasUsingKeyword

  def hasRepeatedParam: Boolean = parameters.lastOption.exists(_.isRepeatedParameter)

  def getSmartParameters: Seq[Parameter] = effectiveParameters.map(Parameter(_))

  /**
    * add parameter as last parameter in clause
    * if clause has repeated parameter, add before this parameter.
    */
  def addParameter(param: ScParameter): ScParameterClause

  def owner: PsiElement

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitParameterClause(this)
  }
}

object ScParameterClause {
  def unapplySeq(e: ScParameterClause): Some[Seq[ScParameter]] = Some(e.parameters)
}