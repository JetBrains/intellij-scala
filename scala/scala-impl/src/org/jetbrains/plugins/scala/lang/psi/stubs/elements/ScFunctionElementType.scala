package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.{ASTNode, Language}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition, ScMacroDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAliasDeclaration, ScGivenAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScFunctionDeclarationImpl, ScFunctionDefinitionImpl, ScMacroDefinitionImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{ScGivenAliasDeclarationImpl, ScGivenAliasDefinitionImpl}

abstract class ScFunctionElementType[Fun <: ScFunction](debugName: String,
                                                        language: Language = ScalaLanguage.INSTANCE)
  extends ScStubElementType[Fun](debugName, language)

final class FunctionDeclaration extends ScFunctionElementType[ScFunctionDeclaration]("function declaration") {
  override def createElement(node: ASTNode): ScFunctionDeclaration = new ScFunctionDeclarationImpl(null, null, node)
}

final class FunctionDefinition extends ScFunctionElementType[ScFunctionDefinition]("function definition") {
  override def createElement(node: ASTNode): ScFunctionDefinition = new ScFunctionDefinitionImpl(null, null, node)
}

final class MacroDefinition extends ScFunctionElementType[ScMacroDefinition]("macro definition") {
  override def createElement(node: ASTNode): ScMacroDefinition = new ScMacroDefinitionImpl(null, null, node)
}

final class GivenAliasDeclaration extends ScFunctionElementType[ScGivenAliasDeclaration]("given alias declaration") {
  override def createElement(node: ASTNode): ScGivenAliasDeclaration = new ScGivenAliasDeclarationImpl(null, null, node)
}

final class GivenAliasDefinition extends ScFunctionElementType[ScGivenAliasDefinition]("given alias definition") {
  override def createElement(node: ASTNode): ScGivenAliasDefinition = new ScGivenAliasDefinitionImpl(null, null, node)
}
