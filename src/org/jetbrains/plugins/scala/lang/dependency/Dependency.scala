package org.jetbrains.plugins.scala
package lang.dependency

import lang.psi.api.base.patterns.ScConstructorPattern
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import extensions._
import com.intellij.psi._
import lang.psi.api.base.{ScReferenceElement, ScPrimaryConstructor}
import lang.psi.api.expr.{ScInfixExpr, ScPostfixExpr}
import lang.psi.api.statements.ScFunctionDefinition
import lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScMember}
import annotator.intention.ScalaImportClassFix

/**
 * Pavel Fatin
 */

case class Dependency(source: PsiElement, target: PsiElement, path: Path) {
  def isExternal = source.getContainingFile != target.getContainingFile

  // TODO Bind references
  // It's better to re-bind references rather than to add imports
  // directly and re-resolve references afterwards.
  // However, current implementation of "bindToElement" can handle only Class references
  def restore() {
    source match {
      case reference: ScReferenceElement if reference.resolve() == null =>
//        reference.bindToElement(target)
        val holder = ScalaImportClassFix.getImportHolder(source, source.getProject)
        holder.addImportForPath(path.asString, source)
      case _ =>
    }
  }
}

case class Path(entity: String, member: Option[String] = None) {
  def asString = member.map("%s.%s".format(entity, _)).getOrElse(entity)
}

object Dependency {
  def dependenciesIn(element: PsiElement): Seq[Dependency] = {
    val references = element.depthFirst.filterByType(classOf[ScReferenceElement]).filter(isPrimary).toList

    references.flatMap { reference =>
      reference.resolve().toOption.flatMap { target =>
        dependencyFor(reference, target)
      }
    }
  }

  private def isPrimary(ref: ScReferenceElement) = ref match {
    case it@Parent(postfix: ScPostfixExpr) => it == postfix.operand
    case it@Parent(infix: ScInfixExpr) => it == infix.lOp
    case it => it.qualifier.isEmpty
  }

  private def dependencyFor(reference: ScReferenceElement, target: PsiElement): Option[Dependency] = {
    def withEntity(entity: String) =
      Some(new Dependency(reference, target, Path(entity)))

    def withMember(entity: String, member: String) =
      Some(new Dependency(reference, target, Path(entity, Some(member))))

    reference match {
      case Parent(_: ScConstructorPattern) =>
        target match {
          case ContainingClass(aClass) =>
            withEntity(aClass.getQualifiedName)
          case aClass: ScSyntheticClass => None
          case _ => None
        }
      case _ =>
        target match {
          case e: ScSyntheticClass =>
            None
          case e: PsiClass =>
            withEntity(e.getQualifiedName)
          case e: PsiPackage =>
            withEntity(e.getQualifiedName)
          case (_: ScPrimaryConstructor) && Parent(e: ScClass) =>
            withEntity(e.getQualifiedName)
          case (function: ScFunctionDefinition) && ContainingClass(obj: ScObject)
            if function.isSynthetic =>
            withEntity(obj.getQualifiedName)
          case (member: ScMember) && ContainingClass(obj: ScObject) =>
            withMember(obj.getQualifiedName, member.getName)
          case (function: ScFunctionDefinition) && ContainingClass(obj: ScClass)
            if function.isConstructor =>
            withEntity(obj.getQualifiedName)
          case (method: PsiMethod) && ContainingClass(e: PsiClass)
            if method.isConstructor =>
            withEntity(e.getQualifiedName)
          case (member: PsiMember) && ContainingClass(e: PsiClass) =>
            withMember(e.getQualifiedName, member.getName)
          case _ => None
        }
    }
  }
}

