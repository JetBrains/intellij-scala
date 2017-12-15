package scala.macros

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.meta.intellij.IdeaUtil

package object intellij {

  implicit class ModuleExt(val module: Module) extends AnyVal {
    def hasMacros2: Boolean = false
  }

  object psiExt {
    implicit class AnnotExt(val annotation: ScAnnotation) extends AnyVal {
      def isMacro2: Boolean = false
    }
  }
}
