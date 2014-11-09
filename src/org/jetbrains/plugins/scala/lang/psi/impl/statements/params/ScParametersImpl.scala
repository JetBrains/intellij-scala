package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClausesStub

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParametersImpl extends ScalaStubBasedElementImpl[ScParameters] with ScParameters {

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScParamClausesStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Parameters"

  def clauses: Seq[ScParameterClause] = {
    getStubOrPsiChildren(ScalaElementTypes.PARAM_CLAUSE, JavaArrayFactoryUtil.ScParameterClauseFactory).toSeq
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

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitParameters(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitParameters(this)
      case _ => super.accept(visitor)
    }
  }

  override def add(element: PsiElement): PsiElement = {
    element match {
      case param: ScParameter =>
        clauses.lastOption match {
          case Some(clause) =>
            clause.addParameter(param).parameters.last
          case _ =>
            val clause = ScalaPsiElementFactory.createClauseFromText("()", getManager)
            val newClause = clause.addParameter(param)
            super.add(clause)
            newClause.parameters.last
        }
      case _ => super.add(element)
    }
  }
}