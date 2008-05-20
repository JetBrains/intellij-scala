package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScParamClauses extends ScParameters with PsiParameterList{

  def params: Seq[ScParameter]
  
  def getParameters = params.toArray

  def getParametersAsString: String

}