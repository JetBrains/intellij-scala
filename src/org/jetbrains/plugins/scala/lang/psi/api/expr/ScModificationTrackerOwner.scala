package org.jetbrains.plugins.scala.lang.psi.api.expr

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.{PsiElement, PsiModifiableCodeBlock}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.annotation.tailrec

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/09/2015
  */
trait ScModificationTrackerOwner extends ScalaPsiElement with PsiModifiableCodeBlock {
  private val blockModificationCount = new AtomicLong(0L)

  def rawModificationCount: Long = blockModificationCount.get()

  def getModificationTracker: ModificationTracker = {
    assert(isValidModificationTrackerOwner)
    new ModificationTracker {
      override def getModificationCount: Long = getModificationCountImpl
    }
  }

  private def getModificationCountImpl: Long = {
    @tailrec
    def calc(place: PsiElement, sum: Long): Long = place match {
      case null => sum + PsiModificationTracker.SERVICE.getInstance(getProject).getOutOfCodeBlockModificationCount
      case file: ScalaFile => sum + file.getManager.getModificationTracker.getOutOfCodeBlockModificationCount
      case owner: ScModificationTrackerOwner if owner.isValidModificationTrackerOwner =>
        calc(owner.getContext, sum + owner.rawModificationCount)
      case _ => calc(place.getContext, sum)
    }

    calc(this, 0L)
  }

  def incModificationCount(): Long = {
    assert(isValidModificationTrackerOwner)
    blockModificationCount.incrementAndGet()
  }

  def isValidModificationTrackerOwner: Boolean = {
    getContext match {
      case f: ScFunction => f.returnTypeElement match {
        case Some(ret) =>  true
        case None => !f.hasAssign
      }
      case v: ScValue => v.typeElement.isDefined
      case v: ScVariable => v.typeElement.isDefined
      case _: ScWhileStmt => true
      case _: ScFinallyBlock => true
      case _: ScDoStmt => true
      case _ => false
    }
  }

  //elem is always the child of this element because this function is called when going up the tree starting with elem
  //if this is a valid modification tracker owner, no need to change modification count
  override def shouldChangeModificationCount(elem: PsiElement) = !isValidModificationTrackerOwner

  @Cached(synchronized = true, ModCount.getBlockModificationCount, this)
  def getMirrorPositionForCompletion(dummyIdentifier: String, pos: Int): Option[PsiElement] = {
    val text = new StringBuilder(getText)
    text.insert(pos, dummyIdentifier)
    val newBlock = ScalaPsiElementFactory.createExpressionWithContextFromText(text.toString(), getContext, this)
    Option(newBlock).map(_.findElementAt(pos))
  }
}

