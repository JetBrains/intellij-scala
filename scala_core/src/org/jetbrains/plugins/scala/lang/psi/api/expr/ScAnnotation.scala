package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiAnnotation
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotation extends ScalaPsiElement with PsiAnnotation {
  def annotationExpr = findChildByClass(classOf[ScAnnotationExpr])
}