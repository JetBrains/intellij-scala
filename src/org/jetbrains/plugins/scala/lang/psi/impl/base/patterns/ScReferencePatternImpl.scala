package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScReferencePatternStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}

/**
 * @author Alexander Podkhalyuzin
 * Date: 28.02.2008
 */
class ScReferencePatternImpl private () extends ScalaStubBasedElementImpl[ScReferencePattern] with ScReferencePattern with ContributedReferenceHost {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScReferencePatternStub) = {this(); setStub(stub); setNode(null)}

  override def isIrrefutableFor(t: Option[ScType]): Boolean = true

  def nameId: PsiElement = findChildByType[PsiElement](TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType[PsiElement](ScalaTokenTypes.tUNDER) != null

  override def toString: String = "ReferencePattern: " + name

  override def getType(ctx: TypingContext) = {
    expectedType match {
      case Some(x) => Success(x, Some(this))
      case _ => Failure("Cannot define expected type", Some(this))
    }
  }

  override def getReferences: Array[PsiReference] = {
    PsiReferenceService.getService.getContributedReferences(this)
  }

  override def getNavigationElement = getContainingFile match {
    case sf: ScalaFile if sf.isCompiled => {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScMember]) // there is no complicated pattern-based declarations in decompiled files
      if (parent != null) {
        val navElem = parent.getNavigationElement
        navElem match {
          case holder: ScDeclaredElementsHolder => holder.declaredElements.find(_.name == name).getOrElse(navElem)
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
        val end = this.nextSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getNextSiblingNotWhitespace.getPrevSibling
        pList.deleteChildRange(this, end)
      case pList: ScPatternList if pList.allPatternsSimple =>
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