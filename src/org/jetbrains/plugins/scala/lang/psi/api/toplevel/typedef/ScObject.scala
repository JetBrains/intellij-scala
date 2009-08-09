package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScObject extends ScTypeDefinition with ScTyped with ScMember {

  override def getContainingClass = null

}