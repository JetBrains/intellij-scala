package org.jetbrains.plugins.scala
package lang
package psi
package uast
package baseAdapters

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.UElement

/**
  * Scala adapter of the [[UElement]].
  * Provides:
  *  - equals & hashCode implementations based on `getSourcePsi`
  *  - default implementation of the [[UElement]]#getUastParent
  *     via abstract `parent: LazyUElement`
  *  - default implementations based on `scElement`
  *  - default implementation of the [[UElement]]#getComments
  *    method based on [[ScUElementWithComments]]
  *
  * @note Just handy util - it is not obligatory to be mixed in by according ScU*** elements.
  * @example inherited by ScU*** elements in [[declarations]] and [[expressions]]
  */
trait ScUElement extends UElement with ScUElementWithComments {

  /**
    * Psi facade of the corresponding UAST type definition.
    *
    * @example [[com.intellij.psi.PsiClass]] for [[org.jetbrains.uast.UClass]]
    */
  type PsiFacade <: PsiElement
  @Nullable
  protected val scElement: PsiFacade

  protected def parent: LazyUElement

  @Nullable
  override def getJavaPsi: PsiFacade = scElement

  @Nullable
  override def getSourcePsi: PsiElement = scElement

  @Nullable
  override def getPsi: PsiFacade = scElement

  @Nullable
  override lazy val getUastParent: UElement = parent.force

  /** equals & hashcode based on the [[getSourcePsi]] */
  def canEqual(other: Any): Boolean =
    other.isInstanceOf[ScUElement]
  override def equals(other: Any): Boolean = other match {
    case that: ScUElement =>
      (that canEqual this) && getSourcePsi == that.getSourcePsi
    case _ => false
  }
  override def hashCode(): Int = {
    val state = Seq(super.hashCode(), getSourcePsi)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def asSourceString(): String =
    Option(scElement).map(_.getText).orNull

  override def toString: String = asLogString()
}
