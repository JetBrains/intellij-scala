package org.jetbrains.plugins.scala.lang.psi.api.statements
package params

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{NullSafe, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGiven, ScGivenDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result._

trait ScParameterClause extends ScalaPsiElement {

  def parameters: Seq[ScParameter]

  def effectiveParameters: Seq[ScParameter]

  def hasParenthesis: Boolean

  //hack: no ClassParamList present at the moment
  def unsafeClassParameters: Seq[ScClassParameter] = effectiveParameters.asInstanceOf[Seq[ScClassParameter]]

  def paramTypes: Seq[ScType] = parameters.map(_.`type`().getOrAny)

  /**
   * Whether the parameters of this clause take contextual arguments and provide contextual values.
   * It's the case if the clause is either marked with using, with implicit, or if it is a given conditional.
   */
  def isImplicit: Boolean = hasImplicitKeyword || hasUsingKeyword || isGivenConditionalClause

  /**
   * Checks whether this parameter clause has an implicit keyword.
   *
   * @note Do not use this to check whether this parameter clause takes contextual arguments or provide contextual values.
   *       Use [[isImplicit]] instead.
   * @return true if this parameter clause has an implicit keyword
   */
  def hasImplicitKeyword: Boolean

  /**
   * Checks whether this parameter clause has a using keyword.
   *
   * @note Do not use this to check whether this parameter clause takes contextual arguments or provide contextual values.
   *       Use [[isImplicit]] instead.
   * @return true if this parameter clause has a using keyword
   */
  def hasUsingKeyword: Boolean

  /**
   * like in
   *   given Int => String = ???   // here Int is wrapped in a ScParameterClause as well
   *         |-|
   * or
   *   given (a: Int) => String = ???
   *         |------|
   */
  def isGivenConditionalClause: Boolean =
    NullSafe(this.getParent).exists(_.getParent.is[ScGiven])

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