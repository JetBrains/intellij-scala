package org.jetbrains.plugins.scala.autoImport.quickFix

import org.jetbrains.plugins.scala.autoImport.GlobalImplicitInstance
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

case class FoundImplicit(instance: GlobalImplicitInstance, path: Seq[ScalaResolveResult], scType: ScType)
