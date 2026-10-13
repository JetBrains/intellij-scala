package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaLanguage

abstract class ScFunctionElementType(debugName: String,
                                     language: Language = ScalaLanguage.INSTANCE)
  extends ScStubElementType(debugName, language)

final class FunctionDeclaration extends ScFunctionElementType("function declaration")

final class FunctionDefinition extends ScFunctionElementType("function definition")

final class MacroDefinition extends ScFunctionElementType("macro definition")

final class GivenAliasDeclaration extends ScFunctionElementType("given alias declaration")

final class GivenAliasDefinition extends ScFunctionElementType("given alias definition")
