package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.project.ProjectExt

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScCaseClauseImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScCaseClause {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

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
          case Some(e: ScInterpolationPattern) => if (!process) return false
          case _ =>
            guard match {
              case Some(g) if lastParent != null &&
                g.startOffsetInParent == lastParent.startOffsetInParent => if (!process) return false
              case _ =>
                //todo: is this good? Maybe parser => always expression.
                val last = findLastChildByType(getProject.tokenSets.membersSet)
                if (last != null && lastParent != null && last.startOffsetInParent == lastParent.startOffsetInParent) {
                  if (!process) return false
                }
            }
        }
      case _ =>
    }
    true
  }
}