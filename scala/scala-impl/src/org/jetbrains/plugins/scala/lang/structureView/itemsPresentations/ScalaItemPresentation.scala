package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations


import javax.swing._

import com.intellij.navigation.ColoredItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
;

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
abstract class ScalaItemPresentation(protected val myElement: PsiElement) extends ColoredItemPresentation {
  def getLocationString: String = null

  def getIcon(open: Boolean): Icon = myElement.getIcon(Iconable.ICON_FLAG_VISIBILITY)

  def getTextAttributesKey: TextAttributesKey = null
}