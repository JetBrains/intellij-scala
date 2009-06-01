package org.jetbrains.plugins.scala.lang.structureView.elements

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.psi.util.PsiTreeUtil
import psi.api.statements.{ScVariable, ScValue}

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

abstract class ScalaStructureViewElement(protected val myElement: PsiElement, val inherited: Boolean) extends StructureViewTreeElement {

  def getValue(): Object = {
    return if (myElement.isValid()) {
      /*
        code for right positioning for caret in case such:
        val x, y = {
          33<caret>
        }
       */
      if (PsiTreeUtil.getParentOfType(myElement, classOf[ScValue]) != null) {
        val v = PsiTreeUtil.getParentOfType(myElement, classOf[ScValue])
        if (myElement.textMatches(v.declaredElements.apply(0))) v
        else myElement
      } else if (PsiTreeUtil.getParentOfType(myElement, classOf[ScVariable]) != null) {
        val v = PsiTreeUtil.getParentOfType(myElement, classOf[ScVariable])
        if (myElement.textMatches(v.declaredElements.apply(0))) v
        else myElement
      } else {
        myElement
      }
    }
    else {
      null;
    }
  }

  def navigate(b: Boolean) {
    myElement.asInstanceOf[Navigatable].navigate(b);
  }

  def canNavigate(): Boolean = {
    return myElement.asInstanceOf[Navigatable].canNavigate();
  }

  def canNavigateToSource(): Boolean = {
    return myElement.asInstanceOf[Navigatable].canNavigateToSource();
  }

  override def equals(o: Any): Boolean = {
    val clazz = if (o.isInstanceOf[Object]) o.asInstanceOf[Object].getClass else return false
    if (o == null || getClass != clazz) return false;
    val that = o.asInstanceOf[ScalaStructureViewElement]
    if (inherited != that.inherited) return false

    val value = getValue
    if (value == null) that.getValue == null
    else value == that.getValue
  }

  override def hashCode(): Int = {
    val value = getValue
    val is = if(inherited) 1 else 0
    if (value == null) 0
    else value.hashCode*2 + is
  }
}