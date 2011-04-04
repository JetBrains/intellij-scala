package org.jetbrains.plugins.scala
package lang
package findUsages

import com.intellij.psi.PsiElement
import psi.api.toplevel.imports.ScImportExpr
import com.intellij.usages.impl.rules.{UsageType, UsageTypeProvider}
import org.jetbrains.plugins.scala.extensions._
import com.intellij.psi.util.PsiTreeUtil
import psi.api.expr.ScNewTemplateDefinition
import psi.api.toplevel.typedef.ScTemplateDefinition
import psi.api.statements.{ScVariable, ScValue, ScPatternDefinition, ScFunction}
import psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import psi.api.statements.params.ScParameter

final class ScalaUsageTypeProvider extends UsageTypeProvider {
  def getUsageType(element: PsiElement): UsageType = {
    import PsiTreeUtil._
    def parentOfType[T <: PsiElement : Manifest]: Option[T] = Option(getParentOfType[T](element, classManifest[T].erasure.asInstanceOf[Class[T]]))
    if (element.containingScalaFile.isDefined) {
      for (ie <- parentOfType[ScImportExpr]) {
        return UsageType.CLASS_IMPORT
      }

      for (ie <- parentOfType[ScNewTemplateDefinition];
           tp <- ie.extendsBlock.templateParents
           if isAncestor(tp, element, false)) {
        return UsageType.CLASS_NEW_OPERATOR
      }

      for (ie <- parentOfType[ScTemplateDefinition];
           tp <- ie.extendsBlock.templateParents
           if isAncestor(tp, element, false)) {
        return UsageType.CLASS_EXTENDS_IMPLEMENTS_LIST
      }

      for (fun <- parentOfType[ScFunction];
           tp <- fun.returnTypeElement
           if isAncestor(tp, element, false)) {
        return UsageType.CLASS_METHOD_RETURN_TYPE
      }

      for (value <- parentOfType[ScValue];
           tp <- value.typeElement
           if isAncestor(tp, element, false)) {
        value.getContext match {
          case _: ScTemplateBody => return UsageType.CLASS_FIELD_DECLARATION
          case _ => return UsageType.CLASS_LOCAL_VAR_DECLARATION
        }
      }

      for (variable <- parentOfType[ScVariable];
           tp <- variable.typeElement
           if isAncestor(tp, element, false)) {
        variable.getContext match {
          case _: ScTemplateBody => return UsageType.CLASS_FIELD_DECLARATION
          case _ => return UsageType.CLASS_LOCAL_VAR_DECLARATION
        }
      }

      for (param <- parentOfType[ScParameter];
           tp <- param.typeElement
           if isAncestor(tp, element, false)) {
        return UsageType.CLASS_METHOD_PARAMETER_DECLARATION
      }

      // TODO more of these, including Scala specific: case class/object, pattern match, type ascription, ...
    }

    return null
  }
}
