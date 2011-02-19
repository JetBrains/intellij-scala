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

  /**
   * Delete this selector from the containing import statement, and
   * add an import statement based on `newQualifier` that contains this selector.
   *
   * moveSelector("newqualifier", "f")
   *
   * Before:
   *
   * import a.b.{c, /*this*/d => e}, x
   *
   * After:
   *
   * import newqualifier.{f => e}
   * import a.b.{c}, x
   *
   */
  def moveSelector(newQualifier: String, newName: String): Unit
}