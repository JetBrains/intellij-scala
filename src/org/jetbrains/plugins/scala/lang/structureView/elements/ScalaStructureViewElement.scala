package org.jetbrains.plugins.scala
package lang
package structureView
package elements

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable};

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

abstract class ScalaStructureViewElement(protected val myElement: PsiElement, val inherited: Boolean) extends StructureViewTreeElement {

  def getValue: Object = {
    if (myElement.isValid) {
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

  def canNavigate: Boolean = {
    myElement.asInstanceOf[Navigatable].canNavigate
  }

  def canNavigateToSource: Boolean = {
    myElement.asInstanceOf[Navigatable].canNavigateToSource
  }

  override def equals(o: Any): Boolean = {
    val clazz = o match {
      case obj: Object => obj.getClass
      case _ => return false
    }
    if (o == null || getClass != clazz) return false
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