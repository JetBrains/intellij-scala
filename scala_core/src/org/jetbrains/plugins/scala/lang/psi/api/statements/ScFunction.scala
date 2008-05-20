package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:45:38
*/

trait ScFunction extends ScalaPsiElement with ScTopStatement with ScTypeParametersOwner with PsiMethod with ScParameterOwner with ScDocCommentOwner {

  def getId: PsiElement

  def getParametersClauses: ScParamClauses

  def getReturnScTypeElement: ScTypeElement

  def typeParametersClause: ScTypeParamClause

  def getFunctionsAndTypeDefs: Seq[ScalaPsiElement]

  def typeParameters: Seq[ScTypeParam] = typeParametersClause.typeParameters

  def getParameters: Seq[ScParameter]


}