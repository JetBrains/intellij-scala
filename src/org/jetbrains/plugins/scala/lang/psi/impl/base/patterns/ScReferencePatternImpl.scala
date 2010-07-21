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
import com.intellij.psi.util.PsiTreeUtil
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
    expectedType match {
      case Some(x) => Success(x, Some(this))
      case _ => Failure("cannot define expected type", Some(this))
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
    ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, getType(TypingContext.empty))
  }

}