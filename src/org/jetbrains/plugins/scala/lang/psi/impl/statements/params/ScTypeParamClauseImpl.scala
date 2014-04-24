package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import psi.stubs.ScTypeParamClauseStub
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import collection.mutable.ArrayBuffer
import scope.PsiScopeProcessor
import lang.resolve.processor.BaseProcessor


/**
* @author Alexander Podkhalyuzin
* @since 22.02.2008
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
        curr match {
          case param: ScTypeParam => buffer += param
          case _ =>
        }
        curr = curr.getNextSibling
      }
      buffer.toSeq
    }
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    if (!processor.isInstanceOf[BaseProcessor]) {
      for (param <- typeParameters) {
        if (!processor.execute(param, state)) return false
      }
    }
    true
  }
}