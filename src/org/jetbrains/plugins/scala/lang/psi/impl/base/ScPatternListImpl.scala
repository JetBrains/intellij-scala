package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base._
import api.base.patterns._
import stubs.{ScPatternListStub}
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScPatternListImpl private () extends ScalaStubBasedElementImpl[ScPatternList] with ScPatternList{

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScPatternListStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ListOfPatterns"

  def patterns: Seq[ScPattern] = findChildrenByClass(classOf[ScPattern])
}