package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

class ScCaseClauseImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScCaseClause {

  override def toString: String = "CaseClause"

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {

    pattern match {
      case Some(p) =>
        def process: Boolean = {
          val iterator = p.bindings.iterator
          while (iterator.hasNext) {
            val b = iterator.next()
            if (!processor.execute(b, state)) return false
          }
          val typeVariablesIterator = p.typeVariables.iterator
          while (typeVariablesIterator.hasNext) {
            val tvar = typeVariablesIterator.next()
            if (!processor.execute(tvar, state)) return false
          }
          true
        }
        expr match {
          case Some(e) if lastParent != null &&
            e.startOffsetInParent == lastParent.startOffsetInParent => if (!process) return false
          case Some(_) if p.is[ScInterpolationPattern] => if (!process) return false
          case _ if lastParent != null =>
            guard match {
              case Some(g) if g.startOffsetInParent == lastParent.startOffsetInParent => if (!process) return false
              case _ =>
                //todo: is this good? Maybe parser => always expression.
                findLastChildByType(TokenSets.MEMBERS) match {
                  case Some(last) if last.startOffsetInParent == lastParent.startOffsetInParent =>
                    if (!process) return false
                  case _ =>
                }
            }
          case _ =>
        }
      case _ =>
    }
    true
  }
}
