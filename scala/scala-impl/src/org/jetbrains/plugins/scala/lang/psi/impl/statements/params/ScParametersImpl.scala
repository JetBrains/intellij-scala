package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createClauseFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClausesStub
import org.jetbrains.plugins.scala.macroAnnotations.Cached

class ScParametersImpl private (stub: ScParamClausesStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.PARAM_CLAUSES, node) with ScParameters {

  def this(node: ASTNode) = this(null, node)
  def this(stub: ScParamClausesStub) = this(stub, null)

  override def toString: String = "Parameters"

  @Cached(ModTracker.anyScalaPsiChange, this)
  override def clauses: Seq[ScParameterClause] = {
    getStubOrPsiChildren(ScalaElementType.PARAM_CLAUSE, JavaArrayFactoryUtil.ScParameterClauseFactory).toSeq
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

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitParameters(this)
  }

  override def add(element: PsiElement): PsiElement = {
    element match {
      case param: ScParameter =>
        clauses.lastOption match {
          case Some(clause) =>
            clause.addParameter(param).parameters.last
          case _ =>
            val clause = createClauseFromText()
            val newClause = clause.addParameter(param)
            super.add(clause)
            newClause.parameters.last
        }
      case _ => super.add(element)
    }
  }
}