package org.jetbrains.idea.devkit.scala.codeInspection.internal

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.{PsiClass, PsiClassType, PsiMethod, PsiType, PsiWildcardType}
import org.jetbrains.idea.devkit.scala.ScalaDevkitBundle
import org.jetbrains.idea.devkit.scala.codeInspection.internal.UnitReturnTypeInSuspendFunctionOverride.isSuspendFunction
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

final class UnitReturnTypeInSuspendFunctionOverride extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case function: ScFunction if function.hasModifierPropertyScala(ScalaModifier.OVERRIDE) && function.hasUnitResultType =>
      function.superMethod.foreach { method =>
        if (isSuspendFunction(method)) {
          holder.registerProblem(function.nameId,
            ScalaDevkitBundle.message("overridden.suspend.function.with.unit.return.type"))
        }
      }
    case _ =>
  }
}

object UnitReturnTypeInSuspendFunctionOverride {
  // See com.intellij.uml.java.utils.UmlKotlinUtils.Coroutines#isSuspendFun
  private def isSuspendFunction(method: PsiMethod): Boolean = {
    if (method.getLanguage.getID.toLowerCase != "kotlin") false
    else {
      val params = method.getParameterList.getParameters
      params.lastOption.exists { parameter =>
        val paramType = parameter.getType
        val unwrappedType = unwrapBoundType(paramType)
        isContinuationType(unwrappedType)
      }
    }
  }

  private def unwrapBoundType(psiType: PsiType): PsiType = psiType match {
    case wildcardType: PsiWildcardType =>
      val bound = wildcardType.getBound
      if (bound == null) psiType
      else bound
    case _ => psiType
  }

  private def isContinuationType(psiType: PsiType): Boolean = psiType match {
    case classType: PsiClassType =>
      classType.resolve() match {
        case resolvedClass: PsiClass =>
          resolvedClass.qualifiedName == "kotlin.coroutines.Continuation"
        case _ => false
      }
    case _ => false
  }
}
