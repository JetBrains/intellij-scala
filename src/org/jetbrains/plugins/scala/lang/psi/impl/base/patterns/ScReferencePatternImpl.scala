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
import com.intellij.psi.util.PsiTreeUtil
import api.toplevel.typedef.ScMember
import api.{ScalaElementVisitor, ScalaFile}
import lang.resolve.processor.BaseProcessor
import api.statements.{ScPatternDefinition, ScDeclaredElementsHolder}
import api.base.ScPatternList
import extensions._

/**
 * @author Alexander Podkhalyuzin
 * Date: 28.02.2008
 */

class ScReferencePatternImpl private () extends ScalaStubBasedElementImpl[ScReferencePattern] with ScReferencePattern {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

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

  override def delete() {
    getContext match {
      case pList: ScPatternList if pList.patterns == Seq(this) =>
        val context: PsiElement = pList.getContext
        context.getContext.deleteChildRange(context, context)
      case pList: ScPatternList if pList.allPatternsSimple && pList.patterns.startsWith(Seq(this)) =>
        val context: PsiElement = pList.getContext
        val end = this.nextSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getNextSiblingNotWhitespace.getPrevSibling
        pList.deleteChildRange(this, end)
      case pList: ScPatternList if pList.allPatternsSimple =>
        val context: PsiElement = pList.getContext
        val start = this.prevSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getPrevSiblingNotWhitespace.getNextSibling
        pList.deleteChildRange(start, this)
      case x =>
        // val (a, b) = t
        // val (_, b) = t
        val anonymousRefPattern = ScalaPsiElementFactory.createWildcardPattern(getManager)
        replace(anonymousRefPattern)
    }
  }

  override def getOriginalElement: PsiElement = super[ScReferencePattern].getOriginalElement
}