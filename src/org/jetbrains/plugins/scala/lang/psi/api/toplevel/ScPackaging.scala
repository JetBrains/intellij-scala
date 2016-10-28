package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

trait ScPackaging extends ScToplevelElement with ScImportsHolder with ScDeclaredElementsHolder with ScPackageLike {
  def parentPackageName: String

  def packageName: String

  def fullPackageName: String = ScPackaging.fullPackageName(parentPackageName, packageName)

  def isExplicit: Boolean

  def packagings: Seq[ScPackaging]

  def typeDefs: Seq[ScTypeDefinition]

  def getBodyText: String

  def reference: Option[ScStableCodeReferenceElement]
}

object ScPackaging {
  def fullPackageName(parentPackageName: String, packageName: String): String = {
    val infix = parentPackageName match {
      case "" => ""
      case _ => "."
    }
    s"$parentPackageName$infix$packageName"
  }
}
