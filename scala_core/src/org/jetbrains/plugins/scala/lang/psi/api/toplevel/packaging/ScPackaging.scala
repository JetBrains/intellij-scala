package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import base.ScStableCodeReferenceElement
import psi.ScalaPsiElement
import api.toplevel.typedef._
import api.toplevel._

trait ScPackaging extends ScToplevelElement with ScImportsHolder {
  def reference = findChildByClass (classOf[ScStableCodeReferenceElement])

  def getPackageName = reference.qualName
}
