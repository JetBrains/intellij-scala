package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import base.ScStableCodeReferenceElement
import psi.ScalaPsiElement
import api.toplevel.typedef._
import api.toplevel._

trait ScPackaging extends ScTypeDefinitionOwner with ScTopStatement with ScNamedElement {

  def getPackageName = reference.qualName

  def reference = findChildByClass (classOf[ScStableCodeReferenceElement])

  override def nameId = reference
  override def name = getPackageName

  def getTopStatements = findChildrenByClass(classOf[ScTopStatement]) 

  def getInnerPackagings = findChildrenByClass(classOf[ScPackaging])
}
