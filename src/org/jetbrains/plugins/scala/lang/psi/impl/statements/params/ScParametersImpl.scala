package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import stubs.ScParamClausesStub;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import scope.PsiScopeProcessor

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParametersImpl extends ScalaStubBasedElementImpl[ScParameters] with ScParameters {

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScParamClausesStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Parameters"

  def params: Seq[ScParameter] = clauses.flatMap((clause: ScParameterClause) => clause.parameters)

  def clauses: Seq[ScParameterClause] = {
    getStubOrPsiChildren(ScalaElementTypes.PARAM_CLAUSE, JavaArrayFactoryUtil.ScParameterClauseFactory).toSeq
  }

  def getParameterIndex(p: PsiParameter) = params.indexOf(List(p))

  def getParametersCount = params.length

  override def getParameters: Array[PsiParameter] = params.toArray

  def addClause(clause: ScParameterClause): ScParameters = {
    getNode.addChild(clause.getNode)
    this
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    if (lastParent != null) {
      val clausesIterator = clauses.iterator
      var break = false
      while (clausesIterator.hasNext && !break) {
        val clause = clausesIterator.next()
        if (clause == lastParent) break = true
        else {
          val paramsIterator = clause.parameters.iterator
          while (paramsIterator.hasNext) {
            val param = paramsIterator.next()
            if (!processor.execute(param, state)) return false
          }
        }
      }
    }
    true
  }
}