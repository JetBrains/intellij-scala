package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import psi.stubs.{ScValueStub, ScVariableStub}
import api.base.types.ScTypeElement
import stubs.elements.wrappers.DummyASTNode
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:55:53
*/

class ScVariableDeclarationImpl extends ScalaStubBasedElementImpl[ScVariable] with ScVariableDeclaration {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScVariableStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScVariableDeclaration"

  def declaredElements = getIdList.fieldIds

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def getIdList: ScIdList = {
    /*val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getIdsContainer match {
        case Some(x) => x
        case None => null
      }
    } else */findChildByClass(classOf[ScIdList])
  }
}