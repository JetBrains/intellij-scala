package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package packaging

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder


trait ScPackaging extends ScToplevelElement with ScImportsHolder with ScPackageContainer with ScDeclaredElementsHolder with ScPackageLike {
  def fullPackageName: String

  def getPackageName: String

  def getBodyText: String

  def reference: Option[ScStableCodeReferenceElement]
}
