package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScIdList extends ScalaPsiElement {
  def fieldIds: Seq[ScFieldId]
}