package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package packaging

import statements.ScDeclaredElementsHolder
import base.ScStableCodeReferenceElement
import psi.ScalaPsiElement
import api.toplevel.typedef._
import api.toplevel._

trait ScPackaging extends ScToplevelElement with ScImportsHolder with ScPackageContainer with ScDeclaredElementsHolder {
  def getPackageName: String

  def isExplicit: Boolean

  def getBodyText: String
}
