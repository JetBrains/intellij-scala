package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScPackagingBase extends statements.ScDeclaredElementsHolderBase with ScPackageLikeBase { this: ScPackaging =>

  def parentPackageName: String

  def packageName: String

  def fullPackageName: String

  def isExplicit: Boolean

  def bodyText: String

  def reference: Option[base.ScStableCodeReference]

  def immediateTypeDefinitions: Seq[toplevel.typedef.ScTypeDefinition]

  def packagings: Seq[ScPackaging]
}

object ScPackagingBase {
  implicit class ScPackagingExt(private val packaging: ScPackaging) extends AnyVal {
    def typeDefinitions: Seq[toplevel.typedef.ScTypeDefinition] =
      packaging.immediateTypeDefinitions ++ packaging.packagings.flatMap(_.typeDefinitions)
  }
}