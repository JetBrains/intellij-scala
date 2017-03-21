package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
* @author ilyas
*/

trait ScModifierListOwner extends ScalaPsiElement with PsiModifierListOwner {

  override def getModifierList: ScModifierList = this.stubOrPsiChild(ScalaElementTypes.MODIFIERS).orNull

  def hasModifierProperty(name: String): Boolean = hasModifierPropertyInner(name)

  def hasModifierPropertyScala(name: String): Boolean = {
    if (name == PsiModifier.PUBLIC)
      !hasModifierPropertyScala("private") && !hasModifierPropertyScala("protected")
    else
      hasModifierPropertyInner(name)
  }

  def hasAbstractModifier: Boolean = hasModifierPropertyInner("abstract")

  def hasFinalModifier: Boolean = hasModifierPropertyInner("final")

  private def hasModifierPropertyInner(name: String): Boolean =
    Option(getModifierList).exists(_.hasModifierProperty(name))

  def setModifierProperty(name: String, value: Boolean) {
    Option(getModifierList).foreach(_.setModifierProperty(name, value))
  }
}