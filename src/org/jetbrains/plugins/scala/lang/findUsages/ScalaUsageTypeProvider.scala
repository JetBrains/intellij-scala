package org.jetbrains.plugins.scala
package lang
package findUsages

import com.intellij.psi.PsiElement
import com.intellij.usages.impl.rules.{UsageType, UsageTypeProviderEx}
import com.intellij.usages.{PsiElementUsageTarget, UsageTarget}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

import scala.reflect.{classTag, ClassTag}

final class ScalaUsageTypeProvider extends UsageTypeProviderEx {
  def getUsageType(element: PsiElement): UsageType = getUsageType(element, null)

  def getUsageType(element: PsiElement, targets: Array[UsageTarget]): UsageType = {
    import com.intellij.psi.util.PsiTreeUtil._
    def parentOfType[T <: PsiElement : ClassTag]: Option[T] = {
      Option(getParentOfType[T](element, classTag[T].runtimeClass.asInstanceOf[Class[T]]))
    }

    if (element.containingScalaFile.isDefined) {

      /** Classify an element found by [[org.jetbrains.plugins.scala.findUsages.parameters.ConstructorParamsInConstructorPatternSearcher]] */
      (element, targets) match {
        case (ref: ScReferenceElement, Array(only: PsiElementUsageTarget)) =>
          ref.bind() match {
            case Some(x) =>
              val targetElement = only.getElement
              if (x.element != targetElement) {
                return UsageType.READ // TODO custom usage type?
              }
            case None =>
          }
        case _ =>
      }

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

      for (consPattern <- parentOfType[ScConstructorPattern];
           tp <- Option(consPattern.ref)
           if isAncestor(tp, element, false)) {
        return ScalaUsageTypeProvider.ClassConstructorPattern
      }

      for (typedPattern <- parentOfType[ScTypedPattern];
           tp <- typedPattern.typePattern
           if isAncestor(tp.typeElement, element, false)) {
        return ScalaUsageTypeProvider.ClassTypedPattern
      }

      // TODO more of these, including Scala specific: case class/object, pattern match, type ascription, ...
    }

    null
  }
}

object ScalaUsageTypeProvider {
  val ClassConstructorPattern: UsageType = new UsageType("Constructor Pattern")
  val ClassTypedPattern: UsageType = new UsageType("Typed Pattern")
}