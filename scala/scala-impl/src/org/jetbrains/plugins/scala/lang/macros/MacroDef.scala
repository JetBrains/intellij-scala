package org.jetbrains.plugins.scala.lang.macros

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScMacroDefinition}

object MacroDef {
  private val macroImpl = "scala.reflect.macros.internal.macroImpl"

  def unapply(named: PsiNamedElement): Option[ScFunction] = {
    named match {
      case f: ScMacroDefinition => Some(f)
      //todo: fix decompiler to avoid this check:
      case f: ScFunction if f.findAnnotationNoAliases(macroImpl) != null => Some(f)
      case _ => None
    }
  }
}
