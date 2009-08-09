package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.psi.api.base._
import api.base.patterns._
import parser.ScalaElementTypes
import stubs.{ScPatternListStub}
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScPatternListImpl private () extends ScalaStubBasedElementImpl[ScPatternList] with ScPatternList{

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScPatternListStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ListOfPatterns"

  def patterns: Seq[ScPattern] = {
    val stub = getStub
    if (stub != null && allPatternsSimple) {
      return stub.getChildrenByType(ScalaElementTypes.REFERENCE_PATTERN, new ArrayFactory[ScReferencePattern] {
        def create(count: Int): Array[ScReferencePattern] = new Array[ScReferencePattern](count)
      })
    }
    return findChildrenByClass(classOf[ScPattern])
  }

  def allPatternsSimple: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScPatternListStub].allPatternsSimple
    }
    return !patterns.exists(p => !(p.isInstanceOf[ScReferencePattern]))
  }
}