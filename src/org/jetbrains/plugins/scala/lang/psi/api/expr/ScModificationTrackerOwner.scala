package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiElement, PsiModifiableCodeBlock}
import org.jetbrains.plugins.scala.caches.{LocalModificationTracker, ScalaSmartModificationTracker}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/09/2015
  */
trait ScModificationTrackerOwner extends ScalaPsiElement with PsiModifiableCodeBlock {

  private val modTrackerKey = Key.create[LocalModificationTracker]("scala.local.modification.tracker")

  def getModificationTracker: ScalaSmartModificationTracker = {
    assert(isValidModificationTrackerOwner)

    Option(this.getUserData(modTrackerKey)).getOrElse {
      val newTracker = new LocalModificationTracker(this)
      this.putUserData(modTrackerKey, newTracker)
      newTracker
    }
  }

  def isValidModificationTrackerOwner: Boolean = {
    getContext match {
      case f: ScFunction => f.returnTypeElement.isDefined || !f.hasAssign
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
  override def shouldChangeModificationCount(elem: PsiElement) = {
    !isValidModificationTrackerOwner
  }

  def createMirror(text: String): PsiElement = {
    ScalaPsiElementFactory.createExpressionWithContextFromText(text, getContext, this)
  }

  @Cached(synchronized = true, ModCount.getBlockModificationCount, this)
  def getMirrorPositionForCompletion(dummyIdentifier: String, pos: Int): Option[PsiElement] = {
    val text = new StringBuilder(getText)
    text.insert(pos, dummyIdentifier)
    val newBlock = createMirror(text.toString())
    Option(newBlock).flatMap(b => Option(b.findElementAt(pos)))
  }
}

