package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates


import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:24:03
*/
trait ScClassParents extends ScTemplateParents {
  def constructor: Option[ScConstructor] = findChild(classOf[ScConstructor])
}