package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

trait ScTemplateDerivesBase extends ScalaPsiElementBase { this: ScTemplateDerives =>
  def deriveReferences: Seq[ScReference]
}