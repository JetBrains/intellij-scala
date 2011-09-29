package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

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
import collection.mutable.ArrayBuffer
import api.toplevel.packaging.ScPackaging


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamClauseImpl extends ScalaStubBasedElementImpl[ScTypeParamClause] with ScTypeParamClause {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeParamClauseStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "TypeParameterClause"

  def getTextByStub: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeParamClauseStub].getTypeParamClauseText
    }
    getText
  }

  def typeParameters: Seq[ScTypeParam] = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.TYPE_PARAM, JavaArrayFactoryUtil.ScTypeParamFactory).toSeq
    } else {
      val buffer = new ArrayBuffer[ScTypeParam]
      var curr = getFirstChild
      while (curr != null) {
        if (curr.isInstanceOf[ScTypeParam]) buffer += curr.asInstanceOf[ScTypeParam]
        curr = curr.getNextSibling
      }
      buffer.toSeq
      //findChildrenByClass(classOf[ScTypeParam]).toSeq
    }
  }
}