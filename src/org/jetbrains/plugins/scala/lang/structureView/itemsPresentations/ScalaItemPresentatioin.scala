package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;

import javax.swing._;

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

abstract class ScalaItemPresentation(protected val myElement: PsiElement) extends ItemPresentation {
  def getLocationString(): String = {
    return null
  }

  def getIcon(open: Boolean): Icon = {
    return myElement.getIcon(Iconable.ICON_FLAG_OPEN)
  }

  def getTextAttributesKey(): TextAttributesKey = {
    return null
  }
}