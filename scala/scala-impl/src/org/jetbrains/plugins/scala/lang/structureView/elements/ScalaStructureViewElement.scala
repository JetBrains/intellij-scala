package org.jetbrains.plugins.scala
package lang
package structureView
package elements

import java.util.Objects

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

abstract class ScalaStructureViewElement[T <: PsiElement](val psiElement: T, val inherited: Boolean) extends StructureViewTreeElement {

  def getValue: Object = if (psiElement.isValid) psiElement else null

  def navigate(b: Boolean) {
    psiElement.asInstanceOf[Navigatable].navigate(b)
  }

  def canNavigate: Boolean = {
    psiElement.asInstanceOf[Navigatable].canNavigate
  }

  def canNavigateToSource: Boolean = {
    psiElement.asInstanceOf[Navigatable].canNavigateToSource
  }

  override def equals(o: Any): Boolean = {
    val clazz = o match {
      case obj: Object => obj.getClass
      case _ => return false
    }
    if (o == null || getClass != clazz) return false
    val that = o.asInstanceOf[ScalaStructureViewElement[_]]
    if (inherited != that.inherited) return false

    val value = getValue
    if (value == null) that.getValue == null
    else value == that.getValue
  }

  override def hashCode(): Int = Objects.hash(getValue, Boolean.box(inherited))
}