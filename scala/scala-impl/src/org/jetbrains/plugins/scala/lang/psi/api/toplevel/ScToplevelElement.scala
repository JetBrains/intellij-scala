package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScPackagingFactory
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.PACKAGING
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * Trait that implements logic by some type definitions aggregation
 *
 * @author ilyas
 */

trait ScToplevelElement extends ScalaPsiElement {

  def typeDefinitions: Seq[ScTypeDefinition] = immediateTypeDefinitions ++ packagings.flatMap(_.typeDefinitions)

  def typeDefinitionsArray: Array[ScTypeDefinition] = typeDefinitions.toArray[ScTypeDefinition]

  def immediateTypeDefinitions: Seq[ScTypeDefinition] = findChildrenByClassScala(classOf[ScTypeDefinition])

  def packagings: Seq[ScPackaging] = this.stubOrPsiChildren(PACKAGING, ScPackagingFactory)

}