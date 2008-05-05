package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:45:38
*/

trait ScFunction extends ScalaPsiElement with ScTopStatement with ScField{
  def getNameNode: ASTNode
  def getParametersClauses: ScParamClauses
  def getReturnTypeNode: ScType
  def getTypeParam: ScTypeParamClause
  def getFunctionsAndTypeDefs: Array[ScalaPsiElement]
  override def getTextOffset(): Int = getNameNode.getTextRange.getStartOffset //todo
  override def getNavigationElement: PsiElement = getNameNode.getPsi
}