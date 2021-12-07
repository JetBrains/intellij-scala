package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}

trait ScPackaging extends ScImportsHolder
  with statements.ScDeclaredElementsHolder
  with ScPackageLike {

  def parentPackageName: String

  def packageName: String

  def fullPackageName: String

  /**
   * @return true - if package has explicit marker (`{` or `:` (Scala 3))<br>
   *         false - otherwise
   * @see [[findExplicitMarker]]
   */
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

  def packagings: Seq[ScPackaging]

  def immediateTypeDefinitions: Seq[ScTypeDefinition]

  def typeDefinitions: Seq[ScTypeDefinition] =
    immediateTypeDefinitions ++ packagings.flatMap(_.typeDefinitions)

  def immediateMembers: Seq[ScMember]

  def members: Seq[ScMember] =
    immediateMembers ++ packagings.flatMap(_.members)
}