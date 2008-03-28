package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.annotations._
/** 
* @author Alexander.Podkhalyuzin
*/

trait ScPackageStatement extends ScalaPsiElement {

  @NotNull
  def getPackageName: String

}