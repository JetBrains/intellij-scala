package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamClauseStub
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.mutable.ArrayBuffer


/**
* @author Alexander Podkhalyuzin
* @since 22.02.2008
*/
class ScTypeParamClauseImpl private (stub: StubElement[ScTypeParamClause], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScTypeParamClause {
  def this(node: ASTNode) = {this(null, null, node)}
  def this(stub: ScTypeParamClauseStub) = {this(stub, ScalaElementTypes.TYPE_PARAM_CLAUSE, null)}

  override def toString: String = "TypeParameterClause"

  def getTextByStub: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeParamClauseStub].typeParameterClauseText
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