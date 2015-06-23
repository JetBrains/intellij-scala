package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPatternListStub
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScPatternListImpl private () extends ScalaStubBasedElementImpl[ScPatternList] with ScPatternList{

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScPatternListStub) = {this(); setStub(stub); setNullNode()}

  override def toString: String = "ListOfPatterns"

  def patterns: Seq[ScPattern] = {
    val stub = getStub
    if (stub != null && allPatternsSimple) {
      return stub.getChildrenByType(ScalaElementTypes.REFERENCE_PATTERN, JavaArrayFactoryUtil.ScReferencePatternFactory)
    }
    findChildrenByClass[ScPattern](classOf[ScPattern])
  }

  def allPatternsSimple: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScPatternListStub].allPatternsSimple
    }
    !patterns.exists(p => !(p.isInstanceOf[ScReferencePattern]))
  }
}