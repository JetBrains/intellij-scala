package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import api.base.patterns._
import api.toplevel.imports.ScImportStmt
import com.intellij.psi.scope.PsiScopeProcessor
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import lang.lexer._
import psi.stubs.ScReferencePatternStub
import psi.types.{ScCompoundType, ScType, Nothing}

/**
 * @author Alexander Podkhalyuzin
 * Date: 28.02.2008
 */

class ScReferencePatternImpl private () extends ScalaStubBasedElementImpl[ScReferencePattern] with ScReferencePattern {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScReferencePatternStub) = {this(); setStub(stub); setNode(null)}

  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType(ScalaTokenTypes.tUNDER) != null

  override def toString: String = "ReferencePattern"

  override def calcType = expectedType match {
    case Some(t) => t
    case None => Nothing
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    def processClassType(t: ScType) = ScType.extractClassType(t) match {
      case Some((c, _)) => c.processDeclarations(processor, state, null, place)
      case _ => true
    }

    lastParent match {
      case _: ScImportStmt => {
        calcType match {
          case ScCompoundType(comps, holders, aliases) => {
            for (t <- comps) if (!processClassType(t)) return false
            for (h <- holders; d <- h.declaredElements) if (!processor.execute(d, state)) return false
            for (a <- aliases) if (!processor.execute(a, state)) return false
            // todo add inner classes!
            true
          }
          case t => processClassType(t)
        }
      }
      case _ => true
    }
  }

}