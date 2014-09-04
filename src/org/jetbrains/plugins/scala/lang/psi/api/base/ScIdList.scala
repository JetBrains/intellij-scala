package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScIdList extends ScalaPsiElement {
  def fieldIds: Seq[ScFieldId]
}