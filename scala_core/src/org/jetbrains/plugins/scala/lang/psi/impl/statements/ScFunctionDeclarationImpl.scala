package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode

import psi.ScalaPsiElementImpl
import api.statements._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:08
*/

class ScFunctionDeclarationImpl(node: ASTNode) extends ScFunctionImpl(node) with ScFunctionDeclaration {

  override def toString: String = "ScFunctionDeclaration"

  def getFunctionsAndTypeDefs = Seq.empty

  def declaredElements = Seq.single(this)

}

