package org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter
import org.jetbrains.plugins.scala.lang.psi.uast.{declarations, expressions}
import org.jetbrains.uast.{UAnchorOwner, UIdentifier}

/**
  * Scala adapter of the [[UAnchorOwner]].
  * Provides:
  *  - default implementations based on `scElement`
  *
  * @note Just handy util - it is not obligatory to be mixed in by according ScU*** elements.
  * @example inherited by some ScU*** elements in [[declarations]] and [[expressions]]
  */
trait ScUAnchorOwner extends UAnchorOwner {
  protected val namedElement: ScNamedElement

  @Nullable
  override def getUastAnchor: UIdentifier =
    BaseScala2UastConverter.createUIdentifier(namedElement.getNameIdentifier, this)
}
