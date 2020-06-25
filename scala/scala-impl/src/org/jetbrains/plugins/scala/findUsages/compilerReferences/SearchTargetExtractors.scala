package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.util.ImplicitUtil.ImplicitSearchTarget
import org.jetbrains.plugins.scala.util.SAMUtil.PsiClassToSAMExt

/**
  * Extractors for entities, references to which do not fit into a text-based search model
  * and thus should be searched via compiler indices.
  *
  * See also: [[ScalaCompilerReferenceService]]
  */
object SearchTargetExtractors {
  sealed trait UsageType {
    override def toString: String = this match {
      case UsageType.SAMInterfaceImplementation => ScalaBundle.message("bytecode.indices.target.sam.type")
      case UsageType.InstanceApplyUnapply       => ScalaBundle.message("bytecode.indices.target.unapply.method")
      case UsageType.ForComprehensionMethods    => ScalaBundle.message("bytecode.indices.target.for.comprehension.method")
      case UsageType.ImplicitDefinitionUsages   => ScalaBundle.message("bytecode.indices.target.implicit.definition")
    }
  }

  object UsageType {
    case object SAMInterfaceImplementation extends UsageType
    case object ForComprehensionMethods    extends UsageType
    case object InstanceApplyUnapply       extends UsageType
    case object ImplicitDefinitionUsages   extends UsageType
  }

  class ShouldBeSearchedInBytecode(settings: CompilerIndicesSettings) {
    def unapply(e: PsiNamedElement): Option[(PsiNamedElement, UsageType)] =
      if (!settings.isBytecodeIndexingActive) None
      else
        e match {
          case ImplicitSearchTarget(idef) if settings.isEnabledForImplicitDefs =>
            Option(idef -> UsageType.ImplicitDefinitionUsages)
          case ForCompehensionMethod(method) if settings.isEnabledForForComprehensionMethods =>
            Option(method -> UsageType.ForComprehensionMethods)
          case SAMType(cls) if settings.isEnabledForSAMTypes => Option(cls -> UsageType.SAMInterfaceImplementation)
          case InstanceApplyUnapply(method) if settings.isEnabledForApplyUnapply =>
            Option(method -> UsageType.InstanceApplyUnapply)
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
      case ScReference(cls: PsiClass) if cls.isSAMable => Option(cls)
      case cls: PsiClass if cls.isSAMable              => Option(cls)
      case _                                           => None
    }
  }
}
