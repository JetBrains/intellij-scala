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
    def hasMacros2: Boolean = {
      OrderEnumerator.orderEntries(module).forEachLibrary(new Processor[Library] {
        override def process(t: Library): Boolean = {
          true
        }
      })
      false
    }
  }


  object psiExt {

    implicit class AnnotExt(val annotation: ScAnnotation) extends AnyVal {
      def isMacro2: Boolean = {
        def isApplyMacro(m: ScMember) = m.isInstanceOf[ScMacroDefinition] && m.getName == "apply"

        IdeaUtil.safeAnnotationResolve(annotation).exists {
          case ScalaResolveResult(c: ScPrimaryConstructor, _) =>
            c.containingClass.members.exists(isApplyMacro)
          case ScalaResolveResult(o: ScTypeDefinition, _) =>
            o.members.exists(isApplyMacro)
          case _ => false
        }
      }
    }

  }

}
