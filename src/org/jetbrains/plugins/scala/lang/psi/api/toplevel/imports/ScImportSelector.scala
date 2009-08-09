package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScImportSelector extends ScalaPsiElement {
  def importedName : String

  def reference : ScStableCodeReferenceElement

  def deleteSelector: Unit
}