package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl.LeftBraceOrColon

trait ScPackaging extends ScImportsHolder
  with statements.ScDeclaredElementsHolder
  with ScPackageLike {

  def parentPackageName: String

  def packageName: String

  def fullPackageName: String

  def isExplicit: Boolean

  /**
   * Scala2: {{{
   * package p1 {
   *   package p2 {
   *   }
   * }
   * }}}
   * Scala3 also supports braceless package, with colon marker: {{{
   *  package p1:
   *    package p2:
   * }}}
   *
   * @return `{` or `:` (in Scala3 braceless syntax)
   */
  def findExplicitMarker: Option[PsiElement]

  def bodyText: String

  def reference: Option[base.ScStableCodeReference]

  def immediateTypeDefinitions: Seq[toplevel.typedef.ScTypeDefinition]

  def packagings: Seq[ScPackaging]
}

object ScPackaging {

  implicit class ScPackagingExt(private val packaging: ScPackaging) extends AnyVal {

    def typeDefinitions: Seq[toplevel.typedef.ScTypeDefinition] =
      packaging.immediateTypeDefinitions ++ packaging.packagings.flatMap(_.typeDefinitions)
  }

}