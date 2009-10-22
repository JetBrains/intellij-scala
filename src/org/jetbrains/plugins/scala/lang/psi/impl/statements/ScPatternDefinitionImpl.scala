package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._
import com.intellij.lang.ASTNode
import stubs.{ScValueStub}

import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.Any
import psi.types.result.TypingContext

/** 
* @author Alexander Podkhalyuzin
*/

class ScPatternDefinitionImpl extends ScalaStubBasedElementImpl[ScValue] with ScPatternDefinition {
  def this(node: ASTNode) = {
    this()
    setNode(node)
  }

  def this(stub: ScValueStub) = {
    this()
    setStub(stub)
    setNode(null)
  }
  
  override def toString: String = "ScPatternDefinition"

  def bindings: Seq[ScBindingPattern] = {
    val plist = this.pList
    if (plist != null) plist.patterns.flatMap((p: ScPattern) => p.bindings) else Seq.empty
  }

  def declaredElements = bindings

  def getType(ctx: TypingContext) = typeElement match {
    case Some(te) => te.cachedType
    case None => expr.getType(ctx)
  }

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScValueStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def pList: ScPatternList = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.PATTERN_LIST, new ArrayFactory[ScPatternList] {
        def create(count: Int): Array[ScPatternList] = new Array[ScPatternList](count)
      }).apply(0)
    } else findChildByClass(classOf[ScPatternList])
  }
}