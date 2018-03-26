package org.jetbrains.plugins.scala
package lang
package structureView
package elements

import java.util.Objects

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

abstract class ScalaStructureViewElement[T <: PsiElement](val element: T, val inherited: Boolean) extends StructureViewTreeElement {

  override def getValue: Object = if (element.isValid) element else null

  override def navigate(b: Boolean) {
    element.asInstanceOf[Navigatable].navigate(b)
  }

  override def canNavigate: Boolean = {
    element.asInstanceOf[Navigatable].canNavigate
  }

  override def canNavigateToSource: Boolean = {
    element.asInstanceOf[Navigatable].canNavigateToSource
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