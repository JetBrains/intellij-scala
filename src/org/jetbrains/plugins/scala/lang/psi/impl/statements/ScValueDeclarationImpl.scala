package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.types.Any
import com.intellij.lang.ASTNode
import stubs.ScValueStub
import api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import psi.types.Nothing
import psi.types.result.TypingContext

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:55:28
 */

class ScValueDeclarationImpl extends ScalaStubBasedElementImpl[ScValue] with ScValueDeclaration {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScValueStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ScValueDeclaration"

  def declaredElements = getIdList.fieldIds

  override def getType(ctx: TypingContext) = wrap(typeElement)(ScalaBundle.message("no.type.element.found", getText)) flatMap {
    te => te.cachedType
  }

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScValueStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def getIdList: ScIdList = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.IDENTIFIER_LIST, new ArrayFactory[ScIdList] {
        def create(count: Int): Array[ScIdList] = new Array[ScIdList](count)
      }).apply(0)
    } else findChildByClass(classOf[ScIdList])
  }
}