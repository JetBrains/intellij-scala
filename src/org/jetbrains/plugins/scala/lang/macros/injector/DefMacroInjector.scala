package org.jetbrains.plugins.scala.lang.macros.injector

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
  * @author mucianm 
  * @since 15.04.16.
  */
trait DefMacroInjector {
    def isApplicable(method: ScFunctionDefinition): Boolean
}
