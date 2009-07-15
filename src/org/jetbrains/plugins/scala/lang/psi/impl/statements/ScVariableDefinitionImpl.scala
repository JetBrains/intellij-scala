package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import stubs.{ScValueStub, ScVariableStub}
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:56:07
*/

class ScVariableDefinitionImpl extends ScalaStubBasedElementImpl[ScVariable] with ScVariableDefinition {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScVariableStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScVariableDefinition"

  def bindings: Seq[ScBindingPattern] = {
    val plist = this.pList
    if (plist != null) plist.patterns.flatMap((p: ScPattern) => p.bindings) else Seq.empty
  }

  def getType = typeElement match {
    case Some(te) => te.cachedType
    case None => expr.cachedType
  }

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def pList: ScPatternList = {
    /*val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getPatternsContainer match {
        case Some(x) => x
        case None => null
      }
    } else */findChildByClass(classOf[ScPatternList])
  }
}