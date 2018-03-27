package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import java.util.Objects

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

abstract class ScalaStructureViewElement[T <: PsiElement](val element: T, val inherited: Boolean) extends StructureViewTreeElement {
  override def getValue: AnyRef = if (element.isValid) element else null

  override def navigate(b: Boolean): Unit = navigatable.foreach(_.navigate(b))

  override def canNavigate: Boolean = navigatable.exists(_.canNavigate)

  override def canNavigateToSource: Boolean = navigatable.exists(_.canNavigateToSource)

  private def navigatable = element.asOptionOf[Navigatable]

  override def getChildren: Array[TreeElement] = TreeElement.EMPTY_ARRAY

  // TODO
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