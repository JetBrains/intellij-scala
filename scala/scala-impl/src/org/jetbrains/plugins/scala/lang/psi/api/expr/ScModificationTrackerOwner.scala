package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import java.util.concurrent.atomic.AtomicLong

import com.intellij.psi.{PsiElement, PsiModifiableCodeBlock}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/09/2015
  */
trait ScModificationTrackerOwner extends ScalaPsiElement with PsiModifiableCodeBlock {
  self => ScExpression

  private[this] val blockModificationCount = new AtomicLong()

  final def modificationCount: Long = blockModificationCount.get

  final def incModificationCount(): Unit = blockModificationCount.incrementAndGet()

  @Cached(ModCount.getBlockModificationCount, this)
  def mirrorPosition(dummyIdentifier: String, offset: Int): Option[PsiElement] = {
    val index = offset - getTextRange.getStartOffset

    val text = new StringBuilder(getText).insert(index, dummyIdentifier).toString

    import impl.ScalaPsiElementFactory.{createConstructorBodyWithContextFromText, createOptionExpressionWithContextFromText}
    val function = this match {
      case _: ScConstrBlock | _: ScConstrExpr =>
        createConstructorBodyWithContextFromText _
      case _ =>
        createOptionExpressionWithContextFromText _
    }

    for {
      block <- function(text, getContext, this)
      element <- Option(block.findElementAt(index))
    } yield element
  }

  //element is always the child of this element because this function is called when going up the tree starting with elem
  //if this is a valid modification tracker owner, no need to change modification count
  override def shouldChangeModificationCount(element: PsiElement): Boolean = getContext match {
    case f: ScFunction => f.returnTypeElement.isEmpty && f.hasAssign
    case v: ScValueOrVariable => v.typeElement.isEmpty
    case _: ScWhileStmt |
         _: ScFinallyBlock |
         _: ScTemplateBody |
         _: ScDoStmt => false
    //expression is not last in a block and not assigned to anything, cannot affect type inference outside
    case _: ScBlock =>
      this.nextSiblings.forall {
        case _: ScExpression => false
        case _ => true
      }
    case _ => true
  }
}

object ScModificationTrackerOwner {

  def unapply(owner: ScModificationTrackerOwner): Boolean =
    !owner.shouldChangeModificationCount(null)
}

