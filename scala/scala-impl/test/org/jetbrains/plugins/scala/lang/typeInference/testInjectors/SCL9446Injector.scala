package org.jetbrains.plugins.scala.lang.typeInference.testInjectors

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class SCL9446Injector extends SyntheticMembersInjector {
  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    if (source.name == "B") Seq("override def foo(): Int = 1")
    else Seq.empty
  }
}

class SCL9446InjectorNoOverride extends SyntheticMembersInjector {
  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    if (source.name == "B") Seq("def foo(): Int = 1")
    else Seq.empty
  }
}