package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScIdList extends ScalaPsiElement {
  def fieldIds: Seq[ScFieldId] = Seq(findChildrenByClass(classOf[ScFieldId]): _*)
}