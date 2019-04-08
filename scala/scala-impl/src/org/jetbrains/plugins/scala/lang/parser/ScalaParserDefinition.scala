package org.jetbrains.plugins.scala
package lang
package parser

import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

final class ScalaParserDefinition extends ScalaParserDefinitionBase(new ScStubFileElementType("scala.file"))
