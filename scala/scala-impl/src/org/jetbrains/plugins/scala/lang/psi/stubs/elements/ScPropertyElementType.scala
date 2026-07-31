package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._

sealed abstract class ScPropertyElementType[P <: ScValueOrVariable](debugName: String)
  extends ScStubElementType[P](debugName)

final class ValueDeclaration extends ScPropertyElementType[ScValueDeclaration]("value declaration") {
  override def createElement(node: ASTNode): ScValueDeclaration = new ScValueDeclarationImpl(null, null, node)
}

final class ValueDefinition extends ScPropertyElementType[ScPatternDefinition]("value definition") {
  override def createElement(node: ASTNode): ScPatternDefinition = new ScPatternDefinitionImpl(null, null, node)
}

final class VariableDeclaration extends ScPropertyElementType[ScVariableDeclaration]("variable declaration") {
  override def createElement(node: ASTNode): ScVariableDeclaration = new ScVariableDeclarationImpl(null, null, node)
}

final class VariableDefinition extends ScPropertyElementType[ScVariableDefinition]("variable definition") {
  override def createElement(node: ASTNode): ScVariableDefinition = new ScVariableDefinitionImpl(null, null, node)
}
