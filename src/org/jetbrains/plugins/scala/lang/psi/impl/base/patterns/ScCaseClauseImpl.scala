package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import api.base.patterns._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import com.intellij.psi._
import impl.source.JavaDummyHolder
import scope.PsiScopeProcessor
import api.ScalaElementVisitor

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
      case Some(p) => {
        def process: Boolean = {
          val iterator = p.bindings.iterator
          while (iterator.hasNext) {
            val b = iterator.next
            if (!processor.execute(b, state)) return false
          }
          true
        }
        expr match {
          case Some(e) if e.getStartOffsetInParent == lastParent.getStartOffsetInParent => if (!process) return false
          case _ =>
            guard match {
              case Some(g) if g.getStartOffsetInParent == lastParent.getStartOffsetInParent => if (!process) return false
              case _ =>
            }
        }
      }
      case _ =>
    }
    true
  }
}