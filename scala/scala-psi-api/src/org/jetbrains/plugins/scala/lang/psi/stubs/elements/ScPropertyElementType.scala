package org.jetbrains.plugins.scala.lang.psi.stubs.elements

sealed abstract class ScPropertyElementType(debugName: String)
  extends ScStubElementType(debugName)

final class ValueDeclaration extends ScPropertyElementType("value declaration")

final class ValueDefinition extends ScPropertyElementType("value definition")

final class VariableDeclaration extends ScPropertyElementType("variable declaration")

final class VariableDefinition extends ScPropertyElementType("variable definition")
