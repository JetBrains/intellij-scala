package org.jetbrains.plugins.scala.findUsages

import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.settings.CompilerIndicesSettings
import org.jetbrains.plugins.scala.util.ImplicitUtil.ImplicitSearchTarget
import org.jetbrains.plugins.scala.util.SAMUtil.PsiClassToSAMExt

/**
  * Extractors for entities, references to which do not fit into a text-based search model
  * and thus should be searched via compiler indices.
  *
  * See also: `ScalaCompilerReferenceService`
  */
object SearchTargetExtractors {
  class ShouldBeSearchedInBytecode(settings: CompilerIndicesSettings) {
    def unapply(e: PsiNamedElement): Option[(PsiNamedElement, UsageType)] =
      if (!settings.isBytecodeIndexingActive) None
      else
        e match {
          case ImplicitSearchTarget(idef) if settings.isEnabledForImplicitDefs && !idef.asOptionOfUnsafe[ScMember].exists(_.isLocal) =>
            // We don't want to search the Bytecode for local implicits.
            val isLocal = idef match {
              case member: ScMember => member.isLocal
              case _: ScParameter => true // ScClassParameters are already processed with ScMember
              case _: ScContextBound => true
              case inNameContext(member: ScMember) => member.isLocal
              case _              => false
            }
            if (isLocal) None else Some(idef -> UsageType.ImplicitDefinitionUsages)
          case ForCompehensionMethod(method) if settings.isEnabledForForComprehensionMethods =>
            Some(method -> UsageType.ForComprehensionMethods)
          case SAMType(cls) if settings.isEnabledForSAMTypes =>
            Some(cls -> UsageType.SAMInterfaceImplementation)
          case InstanceApplyUnapply(method) if settings.isEnabledForApplyUnapply =>
            Some(method -> UsageType.InstanceApplyUnapply)
          case _ => None
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
      case fun: ScFunction if isInstanceApplyUnapply(fun)              => Some(fun)
      case ScReference(fun: ScFunction) if isInstanceApplyUnapply(fun) => Some(fun)
      case _                                                           => None
    }
  }

  object ForCompehensionMethod {
    def unapply(e: PsiElement): Option[ScFunction] = e match {
      case fun: ScFunction if fun.isForComprehensionMethod              => Some(fun)
      case ScReference(fun: ScFunction) if fun.isForComprehensionMethod => Some(fun)
      case _                                                            => None
    }
  }

  object SAMType {
    def unapply(e: PsiElement): Option[PsiClass] = e match {
      case ScReference(cls: PsiClass) if cls.isSAMable => Some(cls)
      case cls: PsiClass if cls.isSAMable              => Some(cls)
      case _                                           => None
    }
  }
}
