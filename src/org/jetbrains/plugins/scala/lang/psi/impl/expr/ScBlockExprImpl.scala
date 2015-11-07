package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr


import java.util
import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.{PsiClass, PsiElement, PsiElementVisitor, PsiModifiableCodeBlock}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount, Cached}

import scala.annotation.tailrec
import scala.collection.mutable.StringBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScBlockExprImpl(text: CharSequence) extends LazyParseablePsiElement(ScalaElementTypes.BLOCK_EXPR, text)
  with ScBlockExpr with PsiModifiableCodeBlock { self =>

  private val blockModificationCount = new AtomicLong(0L)

  //todo: bad architecture to have it duplicated here, as ScBlockExprImpl is not instance of ScalaPsiElementImpl
  override def getContext: PsiElement = {
    context match {
      case null => super.getContext
      case _ => context
    }
  }

  override def toString: String = "BlockExpression"

  override def hasCaseClauses: Boolean = caseClauses.isDefined

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): Array[T] = {
    val result: util.List[T] = new util.ArrayList[T]
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) result.add(cur.asInstanceOf[T])
      cur = cur.getNextSibling
    }
    result.toArray[T](java.lang.reflect.Array.newInstance(aClass, result.size).asInstanceOf[Array[T]])
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    null
  }

  def isModificationCountOwner: Boolean = {
    getContext match {
      case f: ScFunction => f.returnTypeElement match {
        case Some(ret) =>  true
        case None => !f.hasAssign
      }
      case v: ScValue => v.declaredType.isDefined
      case v: ScVariable => v.declaredType.isDefined
      case _ => false
    }
  }

  def incModificationCount(): Long = {
    assert(isModificationCountOwner)
    blockModificationCount.incrementAndGet()
  }

  def shouldChangeModificationCount(place: PsiElement): Boolean = {
    var parent = getParent
    while (parent != null) {
      parent match {
        case f: ScFunction => f.returnTypeElement match {
          case Some(ret) => return false
          case None =>
            if (!f.hasAssign) return false
            return ScalaPsiUtil.shouldChangeModificationCount(f)
        }
        case v: ScValue => return ScalaPsiUtil.shouldChangeModificationCount(v)
        case v: ScVariable => return ScalaPsiUtil.shouldChangeModificationCount(v)
        case t: PsiClass => return true
        case bl: ScBlockExprImpl => return bl.shouldChangeModificationCount(this)
        case _ =>
      }
      parent = parent.getParent
    }
    false
  }

  def getRawModificationCount: Long = blockModificationCount.get()

  def getModificationTracker: ModificationTracker = {
    assert(isModificationCountOwner)
    new ModificationTracker {
      override def getModificationCount: Long = getThisBlockExprModificationCount
    }
  }

  def getThisBlockExprModificationCount: Long = {
    @tailrec
    def calc(place: PsiElement, sum: Long): Long = place match {
      case null => sum + PsiModificationTracker.SERVICE.getInstance(place.getProject).getOutOfCodeBlockModificationCount
      case file: ScalaFile => sum + file.getManager.getModificationTracker.getOutOfCodeBlockModificationCount
      case block: ScBlockExprImpl if block.isModificationCountOwner => calc(block.getContext, sum + block.getRawModificationCount)
      case _ => calc(place.getContext, sum)
    }

    calc(this, 0L)
  }

  override def accept(visitor: ScalaElementVisitor) = {visitor.visitBlockExpression(this)}

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => accept(s)
      case _ => super.accept(visitor)
    }
  }

  @Cached(synchronized = true, ModCount.getModificationCount, this)
  def getMirrorPositionForCompletion(dummyIdentifier: String, pos: Int): PsiElement = {
    val text = new StringBuilder(getText)
    text.insert(pos, dummyIdentifier)
    val newBlock = ScalaPsiElementFactory.createExpressionWithContextFromText(text.toString, getContext, this)
    newBlock.findElementAt(pos)
  }
}