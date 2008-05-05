package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import psi.api.toplevel.typedef._
import psi.api.toplevel._
import org.jetbrains.annotations._

trait ScPackaging extends ScTypeDefinitionOwner with ScTopStatement{

  @NotNull
  def getPackageName: String

  def getTopStatements: Array[ScTopStatement]
}
