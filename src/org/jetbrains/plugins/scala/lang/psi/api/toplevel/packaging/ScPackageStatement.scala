package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
/** 
* @author Alexander.Podkhalyuzin
*/

trait ScPackageStatement extends ScalaPsiElement with ScPackageContainer {

  def setPackageName(name: String)

  def getPackageName: String

  def reference: ScStableCodeReferenceElement = findChildByClass(classOf[ScStableCodeReferenceElement])
}