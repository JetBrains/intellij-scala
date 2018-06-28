package org.jetbrains.plugins.scala.lang.psi.api.expr

import java.util.concurrent.atomic.AtomicLong

import com.intellij.psi.{PsiElement, PsiModifiableCodeBlock}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/09/2015
  */
trait ScModificationTrackerOwner extends ScalaPsiElement with PsiModifiableCodeBlock {
  self => ScExpression

  private val blockModificationCount = new AtomicLong()

  final def modificationCount: Long = ScModificationTrackerOwner.modificationCount(this)

  final def incModificationCount(): Long = blockModificationCount.incrementAndGet()

  @Cached(ModCount.getBlockModificationCount, this)
  def mirrorPosition(dummyIdentifier: String, offset: Int): Option[PsiElement] = {
    val index = offset - getTextRange.getStartOffset

    val text = new StringBuilder(getText)
    text.insert(index, dummyIdentifier)
    createMirror(text.toString(), getContext, this).flatMap { block =>
      Option(block.findElementAt(index))
    }
  }

  //element is always the child of this element because this function is called when going up the tree starting with elem
  //if this is a valid modification tracker owner, no need to change modification count
  override def shouldChangeModificationCount(element: PsiElement): Boolean =
    !isValidModificationTrackerOwner

  protected def createMirror: (String, PsiElement, PsiElement) => Option[ScExpression] =
    ScalaPsiElementFactory.createOptionExpressionWithContextFromText

  private def isValidModificationTrackerOwner: Boolean = getContext match {
    case f: ScFunction        => f.returnTypeElement.isDefined || !f.hasAssign
    case v: ScValueOrVariable => v.typeElement.isDefined
    case _: ScWhileStmt |
         _: ScFinallyBlock |
         _: ScTemplateBody |
         _: ScDoStmt          => true

    //expression is not last in a block and not assigned to anything, cannot affect type inference outside
    case _: ScBlock           => this.nextSiblings.exists(_.isInstanceOf[ScExpression])
    case _                    => false
  }
}

object ScModificationTrackerOwner {

  def unapply(owner: ScModificationTrackerOwner): Boolean =
    owner.isValidModificationTrackerOwner

  @tailrec
  private def modificationCount(place: PsiElement, result: Long = 0)
                               (implicit context: ProjectContext): Long = place match {
    case null | _: ScalaFile => result + ScalaPsiManager.instance(context).getModificationCount
    case _ =>
      val delta = place match {
        case owner@ScModificationTrackerOwner() => owner.blockModificationCount.get()
        case _ => 0
      }
      modificationCount(place.getContext, result + delta)
  }
}

