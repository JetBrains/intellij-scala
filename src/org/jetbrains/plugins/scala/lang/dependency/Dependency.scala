package org.jetbrains.plugins.scala
package lang.dependency

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScPostfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
 * Pavel Fatin
 */

case class Dependency(kind: DependencyKind, source: PsiElement, target: PsiElement, path: Path) {
  def isExternal = source.getContainingFile != target.getContainingFile

  // It's better to re-bind references rather than to add imports
  // directly and re-resolve references afterwards.
  // However, current implementation of "bindToElement" can handle only Class references
  def restoreFor(source: ScReferenceElement) {
    if (source.resolve() != target) {
      source.bindToElement(target)
    }
  }
}

object Dependency {
  def dependenciesIn(scope: PsiElement): Seq[Dependency] = {
    scope.depthFirst
            .filterByType(classOf[ScReferenceElement])
            .toList
            .flatMap(reference => dependencyFor(reference).toList)
  }

  // While we can rely on result.actualElement, there are several bugs related to unapply(Seq)
  // and it's impossible to rebind such targets later (if needed)
  def dependencyFor(reference: ScReferenceElement): Option[Dependency] = {
    if (isPrimary(reference)) {
      reference.bind().flatMap { result =>
        dependencyFor(reference, result.element, result.fromType)
      }
    } else {
      None
    }
  }

  private def isPrimary(ref: ScReferenceElement) = ref match {
    case it@Parent(postfix: ScPostfixExpr) => it == postfix.operand
    case it@Parent(infix: ScInfixExpr) => it == infix.lOp
    case it => it.qualifier.isEmpty
  }

  private def dependencyFor(reference: ScReferenceElement, target: PsiElement, fromType: Option[ScType])
                           (implicit typeSystem: TypeSystem = reference.typeSystem): Option[Dependency] = {
    def withEntity(entity: String) =
      Some(new Dependency(DependencyKind.Reference, reference, target, Path(entity)))

    def withMember(entity: String, member: String) =
      Some(new Dependency(DependencyKind.Reference, reference, target, Path(entity, Some(member))))

    reference match {
      case Parent(_: ScConstructorPattern) =>
        target match {
          case ContainingClass(aClass) =>
            withEntity(aClass.qualifiedName)
          case aClass: ScSyntheticClass => None
          case _ => None
        }
      case _ =>
        target match {
          case e: ScSyntheticClass =>
            None
          case e: PsiClass =>
            withEntity(e.qualifiedName)
          case e: PsiPackage =>
            withEntity(e.getQualifiedName)
          case (_: ScPrimaryConstructor) && Parent(e: ScClass) =>
            withEntity(e.qualifiedName)
          case (function: ScFunctionDefinition) && ContainingClass(obj: ScObject)
            if function.isSynthetic || function.name == "apply" || function.name == "unapply" =>
            withEntity(obj.qualifiedName)
          case (member: ScMember) && ContainingClass(obj: ScObject) =>
            val memberName = member match {
              case named: ScNamedElement => named.name
              case _ => member.getName
            }
            withMember(obj.qualifiedName, memberName)
          case (pattern: ScReferencePattern) && Parent(Parent(ContainingClass(obj: ScObject))) =>
            withMember(obj.qualifiedName, pattern.name)
          case (function: ScFunctionDefinition) && ContainingClass(obj: ScClass)
            if function.isConstructor =>
            withEntity(obj.qualifiedName)
          case (method: PsiMethod) && ContainingClass(e: PsiClass)
            if method.isConstructor =>
            withEntity(e.qualifiedName)
          case (method: PsiMember) && ContainingClass(e: PsiClass)
            if method.getModifierList.hasModifierProperty("static") =>
            withMember(e.qualifiedName, method.getName)
          case (member: PsiMember) && ContainingClass(e: PsiClass) =>
            fromType.flatMap(_.extractClass(e.getProject)) match {
              case Some(entity: ScObject) =>
                val memberName = member match {
                  case named: ScNamedElement => named.name
                  case _ => member.getName
                }
                withMember(entity.qualifiedName, memberName)
              case _ => None
            }
          case _ => None
        }
    }
  }
}

