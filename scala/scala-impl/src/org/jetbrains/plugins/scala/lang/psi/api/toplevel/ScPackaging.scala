package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.{ScBegin, ScPackageLike}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScOptionalBracesOwner, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScExtension}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}

trait ScPackaging extends ScImportsHolder
  with ScDeclaredElementsHolder
  with ScPackageLike
  with ScOptionalBracesOwner
  with ScBegin {

  def parentPackageName: String

  /**
   * @return name of the packaging<br>
   * @note for the fully qualified name use [[fullPackageName]]
   * @example {{{
   *   package aaa.bbb
   *   package ccc.ddd //results "ccc.ddd"
   *   class MyClass
   * }}}
   */
  def packageName: String

  /**
   * @return fully qualified name of the packaging
   * @example {{{
   *   package aaa.bbb
   *   package ccc.ddd //results "aaa.bbb.ccc.ddd"
   *   class MyClass
   * }}}
   */
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

  def reference: Option[ScStableCodeReference]

  def packagings: Seq[ScPackaging]

  def immediateTypeDefinitions: Seq[ScTypeDefinition]

  def typeDefinitions: Seq[ScTypeDefinition] =
    immediateTypeDefinitions ++ packagings.flatMap(_.typeDefinitions)

  def immediateMembers: Seq[ScMember]

  def immediateExtensions: Seq[ScExtension]

  def members: Seq[ScMember] =
    immediateMembers ++ packagings.flatMap(_.members)

  def extensions: Seq[ScExtension] =
    immediateExtensions ++ packagings.flatMap(_.extensions)
}
