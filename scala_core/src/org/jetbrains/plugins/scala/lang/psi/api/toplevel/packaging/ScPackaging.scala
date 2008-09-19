package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import statements.ScDeclaredElementsHolder
import base.ScStableCodeReferenceElement
import psi.ScalaPsiElement
import api.toplevel.typedef._
import api.toplevel._

trait ScPackaging extends ScToplevelElement with ScImportsHolder with ScPackageContainer with ScDeclaredElementsHolder {
  def getPackageName: String
}
