package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._

/**
* @author ilyas
*/

//wrapper over identifier for variable declarations 'var v : T' 
trait ScFieldId extends ScNamedElement {
}