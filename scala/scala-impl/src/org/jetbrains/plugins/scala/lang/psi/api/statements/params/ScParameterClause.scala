package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author Alexander Podkhalyuzin
  * Date: 21.03.2008
  */
trait ScParameterClause extends ScalaPsiElement {

  def parameters: Seq[ScParameter]

  def effectiveParameters: Seq[ScParameter]

  def hasParenthesis: Boolean

  //hack: no ClassParamList present at the moment
  def unsafeClassParameters: Seq[ScClassParameter] = effectiveParameters.asInstanceOf[Seq[ScClassParameter]]

  def paramTypes: Seq[ScType] = parameters.map(_.`type`().getOrAny)

  // TODO: a lot of places that depend on isImplicit should also be triggered when `isUsing` is true.
  //  Review all `isImplicit` method usages and see if it actually should be something like `isImplicitOrUsing`
  //  Also, maybe method names should be reviewed or even split to several methods:
  //  `isImplicit` could express the semantics (parameter clause with `using` is also kinda implicit, just with a different keyword
  //  In those places, where the semantics is important and the keyword is not, we could use `isImplicit`
  //  In those places, whether the keyword is important we could use `hasImplicitKeyword` / `hasUsingKeyword`
  //  Or we could create some method `def implicitKind: Option[ImplicitKeyword | UsingKeyword]`
  def isImplicit: Boolean
  def isUsing: Boolean

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