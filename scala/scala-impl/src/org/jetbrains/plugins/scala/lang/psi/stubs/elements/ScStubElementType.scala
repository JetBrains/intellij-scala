package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.tree.IScalaElementType

/**
 * Base for Scala element types whose stub support is registered separately via a
 * [[com.intellij.psi.stubs.StubSerializingElementFactory]] (see
 * [[org.jetbrains.plugins.scala.lang.psi.stubs.ScalaStubRegistryExtension]]), instead of extending
 * [[com.intellij.psi.stubs.IStubElementType]].
 *
 * Such element types are plain [[com.intellij.psi.tree.IElementType]]s and know nothing about PSI:
 * PSI elements are created by [[org.jetbrains.plugins.scala.lang.parser.ASTNodeToPsiElement]] (from AST)
 * and by the stub factories (from stubs). This keeps them independent of the PSI implementation.
 */
abstract class ScStubElementType(debugName: String, language: Language = ScalaLanguage.INSTANCE)
  extends IScalaElementType(debugName, language)
