package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import base.ScStableCodeReferenceElement
import psi.ScalaPsiElement
import api.toplevel.typedef._
import api.toplevel._

trait ScPackaging extends ScToplevelElement with ScImportsHolder with ScPackageContainer {
  def getPackageName: String
}
