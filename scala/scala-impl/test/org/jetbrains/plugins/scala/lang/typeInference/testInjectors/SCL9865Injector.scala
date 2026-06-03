package org.jetbrains.plugins.scala.lang.typeInference.testInjectors

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class SCL9865Injector extends SyntheticMembersInjector {
  override def injectSupers(source: ScTypeDefinition): Seq[String] = {
    if (source.name == "A") Seq("something.B")
    else Seq.empty
  }
}
