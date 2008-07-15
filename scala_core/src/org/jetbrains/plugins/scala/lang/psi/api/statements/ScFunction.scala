package org.jetbrains.plugins.scala.lang.psi.api.statements

import types.ScType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:45:38
*/

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun {
  def retType : ScType
  def paramTypes : Seq[ScType]
}

trait ScFunction extends ScalaPsiElement with ScNamedElement with ScMember 
        with ScTopStatement with ScTypeParametersOwner
        with PsiMethod with ScParameterOwner with ScDocCommentOwner with ScTyped {

  def paramClauses: ScParameters

  def returnTypeElement = findChild(classOf[ScTypeElement])

  def parameters: Seq[ScParameter]

  def getModifierList(): ScModifierList

  def paramTypes = parameters.map{_.calcType}
}