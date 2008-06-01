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

trait ScFunction extends ScalaPsiElement with ScNamedElement with ScTopStatement with ScTypeParametersOwner with PsiMethod with ScParameterOwner with ScDocCommentOwner {

  def paramClauses: ScParameters

  def getReturnScTypeElement: ScTypeElement

  def getFunctionsAndTypeDefs: Seq[ScalaPsiElement]

  def parameters: Seq[ScParameter]


}