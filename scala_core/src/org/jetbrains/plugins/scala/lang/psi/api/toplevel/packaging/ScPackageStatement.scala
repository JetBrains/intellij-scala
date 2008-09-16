package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
/** 
* @author Alexander.Podkhalyuzin
*/

trait ScPackageStatement extends ScalaPsiElement with ScPackageContainer {

  def getPackageName: String

  def reference = findChildByClass(classOf[ScStableCodeReferenceElement])
}