package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.PsiModifierList
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.util.EnumSet._

trait ScModifierList extends ScalaPsiElement with PsiModifierList {

  //only one access modifier can occur in a particular modifier list
  def accessModifier: Option[ScAccessModifier]

  def modifiers: EnumSet[ScalaModifier]
}

object ScModifierList {

  implicit class ScModifierListExt(private val list: ScModifierList) extends AnyVal {

    import ScalaModifier._

    def isFinal: Boolean = hasModifier(Final)

    def isAbstract: Boolean = hasModifier(Abstract)

    def isOverride: Boolean = hasModifier(Override)

    def isImplicit: Boolean = hasModifier(Implicit)

    def isSealed: Boolean = hasModifier(Sealed)

    def isLazy: Boolean = hasModifier(Lazy)

    def isCase: Boolean = hasModifier(Case)

    def isPrivate: Boolean = hasModifier(Private)

    def isProtected: Boolean = hasModifier(Protected)

    def isInline: Boolean = hasModifier(Inline)

    def isTransparent: Boolean = hasModifier(Transparent)

    def isOpaque: Boolean = hasModifier(Opaque)

    private def hasModifier(value: ScalaModifier) =
      list.modifiers.contains(value)
  }

}