package org.jetbrains.plugins.scala.lang.structureView.element

import java.util.Objects

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement

abstract class AbstractTreeElement[T <: PsiElement](val element: T, val inherited: Boolean = false)
  extends Element with AbstractNavigatable with AbstractItemPresentation {

  override def getPresentation: ItemPresentation = this

  override def getValue: AnyRef = if (element.isValid) element else null

  override def getChildren: Array[TreeElement] = TreeElement.EMPTY_ARRAY

  // TODO
  override def equals(o: Any): Boolean = {
    val clazz = o match {
      case obj: Object => obj.getClass
      case _ => return false
    }
    if (o == null || getClass != clazz) return false
    val that = o.asInstanceOf[Element]
    if (inherited != that.inherited) return false

    val value = getValue
    if (value == null) that.getValue == null
    else value == that.getValue
  }

  override def hashCode(): Int = Objects.hash(getValue, Boolean.box(inherited))
}

