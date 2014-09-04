package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScEnumerators extends ScalaPsiElement {

  def enumerators: Seq[ScEnumerator]

  def generators: Seq[ScGenerator]

  def guards: Seq[ScGuard]

  def namings: Seq[ScPatterned]
}