package org.jetbrains.plugins.scala
package lang.refactoring.move

import extensions._
import lang.psi.api.expr.{ScInfixExpr, ScPostfixExpr}
import lang.psi.api.base.patterns.ScConstructorPattern
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiReferenceExpression, PsiClass, PsiElement}
import lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}
import annotator.intention.ScalaImportClassFix

/**
 * Pavel Fatin
 */

object ScalaContextUtil {
  private val REF_CLASS_KEY: Key[PsiClass] = Key.create("REF_CLASS_KEY")

  def decode(scope: PsiElement) {
    scope.depthFirst.foreach {
      case element: ScReferenceElement =>
        val refClass = element.getCopyableUserData(REF_CLASS_KEY)
        element.putCopyableUserData(REF_CLASS_KEY, null)

        if (refClass != null && refClass.isValid) {
          val holder = ScalaImportClassFix.getImportHolder(element, element.getProject)
          holder.addImportForPath(refClass.getQualifiedName, element)
//          element.bindToElement(refClass).asInstanceOf[PsiReferenceExpression]
        }
      case _ =>
    }
  }

  def encode(scope: PsiElement) {
    val l = scope.depthFirst.toList
    val refs = l.filterBy(classOf[ScReferenceElement])
    for (reference <- refs if isPrimary(reference);
         target <- reference.resolve().toOption)
      encode(reference, target)
  }

  private def isPrimary(ref: ScReferenceElement) = ref match {
    case it@Parent(postfix: ScPostfixExpr) => it == postfix.operand
    case it@Parent(infix: ScInfixExpr) => it == infix.lOp
    case it => it.qualifier.isEmpty
  }

  private def encode(reference: ScReferenceElement, target: PsiElement) {
    reference match {
      case Parent(_: ScConstructorPattern) =>
        target match {
          case ContainingClass(aClass) =>
          //            Some(PatternDependency(element, startOffset, aClass.getQualifiedName))
          case aClass: ScSyntheticClass =>
          //            Some(PatternDependency(element, startOffset, aClass.getQualifiedName))
          case _ => None
        }
      case _ =>
        Some(target) collect pf(
        {case e: PsiClass =>
            reference.putCopyableUserData(REF_CLASS_KEY, e)},

          // workaround for scalac pattern matcher bug. See SCL-3150
          //          TypeDependency(element, startOffset, e.getQualifiedName)},
          //        {case e: PsiPackage => PackageDependency(element, startOffset, e.getQualifiedName)},
                  {case Both(_: ScPrimaryConstructor, Parent(parent: PsiClass)) =>
                    reference.putCopyableUserData(REF_CLASS_KEY, parent)}
//                    PrimaryConstructorDependency(element, startOffset, parent.getQualifiedName)},
//                  {case Both(member: ScMember, ContainingClass(obj: ScObject)) =>
          //          MemberDependency(element, startOffset, obj.getQualifiedName, member.getName)},
          //        {case Both(method: PsiMethod, ContainingClass(aClass: PsiClass)) if method.isConstructor =>
          //          TypeDependency(element, startOffset, aClass.getQualifiedName)},
          //        {case Both(member: PsiMember, ContainingClass(aClass: PsiClass)) =>
          //          MemberDependency(element, startOffset, aClass.getQualifiedName, member.getName)}

        )

    }

  }
}