package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScPackagingFactory
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.PACKAGING
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * Trait that implements logic by some type definitions aggregation
 *
 * @author ilyas
 */

trait ScToplevelElement extends ScImportsHolder {

  def immediateTypeDefinitions: Seq[ScTypeDefinition]

  def packagings: Seq[ScPackaging] = this.stubOrPsiChildren(PACKAGING, ScPackagingFactory)

}

object ScToplevelElement {

  implicit class TopElementExt(private val element: ScToplevelElement) extends AnyVal {

    def typeDefinitions: Seq[ScTypeDefinition] =
      element.immediateTypeDefinitions ++ element.packagings.flatMap(_.typeDefinitions)
  }

}