package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder

trait ScPackaging extends ScToplevelElement
  with ScDeclaredElementsHolder
  with ScPackageLike {

  def parentPackageName: String

  def packageName: String

  def fullPackageName: String

  def isExplicit: Boolean

  def bodyText: String

  def reference: Option[ScStableCodeReferenceElement]
}