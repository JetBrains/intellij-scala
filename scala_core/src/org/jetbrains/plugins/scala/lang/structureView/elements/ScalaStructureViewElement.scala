package org.jetbrains.plugins.scala.lang.structureView.elements

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

abstract class ScalaStructureViewElement(protected val myElement: PsiElement) extends StructureViewTreeElement {

  def getValue(): Object = {
    return if (myElement.isValid()) 
      myElement
    else
      null;
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
}