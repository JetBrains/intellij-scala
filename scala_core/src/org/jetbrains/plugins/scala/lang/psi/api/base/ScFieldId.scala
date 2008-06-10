package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

//wrapper over identifier for variable declarations 'var v : T' 
trait ScFieldId extends ScNamedElement {
}