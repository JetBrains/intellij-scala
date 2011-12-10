package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package packaging

import statements.ScDeclaredElementsHolder
import base.ScStableCodeReferenceElement
import api.toplevel._


trait ScPackaging extends ScToplevelElement with ScImportsHolder with ScPackageContainer with ScDeclaredElementsHolder with ScPackageLike {
  def fullPackageName: String

  def getPackageName: String

  def isExplicit: Boolean

  def getBodyText: String

  def reference: Option[ScStableCodeReferenceElement]

  def strip()
}
