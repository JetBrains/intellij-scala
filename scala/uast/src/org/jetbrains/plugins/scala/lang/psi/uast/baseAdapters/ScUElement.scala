package org.jetbrains.plugins.scala
package lang
package psi
package uast
package baseAdapters

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.UElement
import org.jetbrains.plugins.scala.util.HashBuilder._

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

  override def equals(other: Any): Boolean = other match {
    case other: ScUElement =>
      getSourcePsi == other.getSourcePsi
    case _ => false
  }

  override def hashCode(): Int =
    super.hashCode #+ getSourcePsi

  override def asSourceString(): String =
    Option(scElement).map(_.getText).orNull

  override def toString: String = asLogString()
}
