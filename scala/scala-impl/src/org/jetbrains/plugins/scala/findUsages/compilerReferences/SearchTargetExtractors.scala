package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.util.ImplicitUtil.ImplicitSearchTarget
import org.jetbrains.plugins.scala.util.SAMUtil

object SearchTargetExtractors {
  class ShouldBeSearchedInBytecode(settings: CompilerIndicesSettings) {
    def unapply(e: PsiNamedElement): Option[PsiNamedElement] = e match {
      case ImplicitSearchTarget(idef)    if settings.isEnabledForImplicitDefs   => Option(idef)
      case ForCompehensionMethod(method) if settings.isEnabledForForCompMethods => Option(method)
      case SAMType(cls)                  if settings.isEnabledForSAMTypes       => Option(cls)
      case InstanceApplyUnapply(method)  if settings.isEnabledForApplyUnapply   => Option(method)
      case _                                                                    => None
    }
  }

  object InstanceApplyUnapply {
    private[this] def isInstance(member: ScMember): Boolean =
      member.containingClass.toOption.exists {
        case _: ScObject => false
        case _           => true
      }

    private[this] def isInstanceApplyUnapply(f: ScFunction): Boolean =
      isInstance(f) && (f.isApplyMethod || f.isUnapplyMethod)

    def unapply(e: PsiElement): Option[ScFunction] = e match {
      case fun: ScFunction if isInstanceApplyUnapply(fun)              => Option(fun)
      case ScReference(fun: ScFunction) if isInstanceApplyUnapply(fun) => Option(fun)
      case _                                                           => None
    }
  }

  object ForCompehensionMethod {
    def unapply(e: PsiElement): Option[ScFunction] = e match {
      case fun: ScFunction if fun.isForComprehensionMethod              => Option(fun)
      case ScReference(fun: ScFunction) if fun.isForComprehensionMethod => Option(fun)
      case _                                                            => None
    }
  }

  object SAMType {
    def unapply(e: PsiElement): Option[PsiClass] = e match {
      case ScReference(cls: PsiClass) if SAMUtil.isSAMable(cls) => Option(cls)
      case cls: PsiClass if SAMUtil.isSAMable(cls)              => Option(cls)
      case _                                                    => None
    }
  }
}
