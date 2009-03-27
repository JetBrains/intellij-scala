package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScForStatement extends ScExpression {

  def enumerators: Option[ScEnumerators]

  def patterns: Seq[ScPattern]

}