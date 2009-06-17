package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import psi.stubs.{ScTypeParamClauseStub, ScModifiersStub}

import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.statements.params._


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamClauseImpl extends ScalaStubBasedElementImpl[ScTypeParamClause] with ScTypeParamClause {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeParamClauseStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "TypeParameterClause"

  def typeParameters(): Seq[ScTypeParam] = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.TYPE_PARAM, new ArrayFactory[ScTypeParam] {
        def create(count: Int): Array[ScTypeParam] = new Array[ScTypeParam](count)
      }).toSeq
    } else findChildrenByClass(classOf[ScTypeParam])
  }
}