package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:45:29
*/

trait ScVariable extends ScalaPsiElement with ScField{
  def getIdentifierNodes: Array[PsiElement]
}