package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPropertyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScPropertyElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

final class ScVariableDeclarationImpl private[psi] (
  stub:     ScPropertyStub[ScVariableDeclaration],
  nodeType: ScPropertyElementType[ScVariableDeclaration],
  node:     ASTNode
) extends ScValueOrVariableImpl(stub, nodeType, node)
    with ScVariableDeclaration {

  override def toString: String = "ScVariableDeclaration: " + ifReadAllowed(declaredNames.mkString(", "))("")

  override def `type`(): TypeResult = this.flatMapType(typeElement)

  override def declaredElements: Seq[ScFieldId] = getIdList.fieldIds

  override def typeElement: Option[ScTypeElement] = byPsiOrStub(findChild[ScTypeElement])(_.typeElement)

  override def getIdList: ScIdList = getStubOrPsiChild(ScalaElementType.IDENTIFIER_LIST)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitVariableDeclaration(this)
  }
}