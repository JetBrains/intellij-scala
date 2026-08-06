package org.jetbrains.plugins.scala.lang.typeInference.testInjectors

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class SCL9445Injector extends SyntheticMembersInjector {
  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    if (source.name.startsWith("SCL")) Seq("case class Bar(x: Int)")
    else Seq.empty
  }
}
