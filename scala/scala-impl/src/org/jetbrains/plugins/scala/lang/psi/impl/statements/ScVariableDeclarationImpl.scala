package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPropertyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScPropertyElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import scala.annotation.nowarn

final class ScVariableDeclarationImpl private[psi] (
  stub:     ScPropertyStub[ScVariableDeclaration],
  nodeType: ScPropertyElementType[ScVariableDeclaration],
  node:     ASTNode
) extends ScValueOrVariableImpl(stub, nodeType, node)
    with ScVariableDeclaration {

  override def toString: String = "ScVariableDeclaration: " + ifReadAllowed(declaredNames.mkString(", "))("")

  override def `type`(): TypeResult = this.flatMapType(typeTreeHolder)

  override def declaredElements: Seq[ScFieldId] = getIdList.fieldIds

  override def typePsiElement: Option[ScTypeElement] = findChild[ScTypeElement]
  override def typeTreeHolder: Option[TypeTreeHolder] = byStubOrPsi(_.typeTreeHolder)(typePsiElement)

  override def getIdList: ScIdList = getStubOrPsiChild(ScalaElementType.IDENTIFIER_LIST): @nowarn("cat=deprecation") // IJPL-562

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitVariableDeclaration(this)
  }
}