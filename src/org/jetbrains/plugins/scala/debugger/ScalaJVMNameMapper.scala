package org.jetbrains.plugins.scala
package debugger

import com.intellij.psi.PsiClass
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject, ScTemplateDefinition}
import com.intellij.openapi.util.Computable
import org.jetbrains.annotations.NotNull
import com.intellij.debugger.NameMapper

/**
*@author ilyas
*/
class ScalaJVMNameMapper extends NameMapper {
  def getQualifiedName(@NotNull clazz: PsiClass): String = {
    ApplicationManager.getApplication.runReadAction(new Computable[String] {
      def compute: String = {
        clazz match {
          case obj: ScObject => obj.qualifiedName + "$"
          case tr: ScTrait => tr.qualifiedName
          case templDef: ScTemplateDefinition => templDef.qualifiedName
          case psiClass => psiClass.getQualifiedName
        }
      }
    })
  }
}
