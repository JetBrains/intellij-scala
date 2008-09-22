package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiMethod
import javax.swing.Icon

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.09.2008
 */

class OverrideGutter(methods: Array[PsiMethod], isImplements: Boolean) extends GutterIconRenderer {
  def getIcon: Icon = if (isImplements) IconLoader.getIcon("/gutter/implementingMethod.png");
                      else IconLoader.getIcon("/gutter/overridingMethod.png")
}