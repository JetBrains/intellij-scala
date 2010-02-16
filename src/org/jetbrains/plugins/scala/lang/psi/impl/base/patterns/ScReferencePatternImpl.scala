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
import psi.types.result.{Failure, TypingContext, Success}
import psi.types.{ScSubstitutor, ScCompoundType, ScType, Nothing}
import api.ScalaFile
import util.PsiTreeUtil
import api.toplevel.typedef.ScMember
import api.statements.ScDeclaredElementsHolder

/**
 * @author Alexander Podkhalyuzin
 * Date: 28.02.2008
 */

class ScReferencePatternImpl private () extends ScalaStubBasedElementImpl[ScReferencePattern] with ScReferencePattern {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScReferencePatternStub) = {this(); setStub(stub); setNode(null)}

  override def isIrrefutableFor(t: Option[ScType]): Boolean = true

  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType(ScalaTokenTypes.tUNDER) != null

  override def toString: String = "ReferencePattern"

  override def getType(ctx: TypingContext) = {
    //rewrited because of Scala Compiler bugs (NPE was here)
    expectedType match {
      case Some(x) => Success(x, Some(this))
      case None => Failure("cannot define expected type", Some(this))
    }
  }


  override def getNavigationElement = getContainingFile match {
    case sf: ScalaFile if sf.isCompiled => {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScMember]) // there is no complicated pattern-based declarations in decompiled files
      if (parent != null) {
        val navElem = parent.getNavigationElement
        navElem match {
          case holder: ScDeclaredElementsHolder => holder.declaredElements.find(_.getName == name).getOrElse(navElem)
          case x => x
        }
      }
      else super.getNavigationElement
    }
    case _ => super.getNavigationElement
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    def processClassType(t: ScType) = ScType.extractClass(t) match {
      case Some(c) => c.processDeclarations(processor, state, null, place)
      case _ => true
    }

    lastParent match {
      case _: ScImportStmt => {
        getType(TypingContext.empty) match {
          case Success(ScCompoundType(comps, holders, aliases, substitutor), _) => {
            for (t <- comps) if (!processClassType(t)) return false
            val currentSubst = state.get(ScSubstitutor.key)
            val newState = state.put(ScSubstitutor.key, substitutor.followed(currentSubst))
            for (h <- holders; d <- h.declaredElements) if (!processor.execute(d, newState)) return false
            for (a <- aliases) if (!processor.execute(a, newState)) return false
            // todo add inner classes!
            true
          }
          case Success(t, _) => processClassType(t)
          case _ => true
        }
      }
      case _ => true
    }
  }

}