package org.jetbrains.plugins.scala.lang.refactoring.move.members

import java.util

import com.intellij.psi._
import com.intellij.refactoring.move.moveMembers.{MoveJavaMemberHandler, MoveMembersOptions, MoveMembersProcessor}
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaChangeContextUtil

class ScalaMoveMemberHandler extends MoveJavaMemberHandler {

  override def checkConflictsOnMember(scRefPattern: PsiMember, newVisibility: String, modifiedListCopy: PsiModifierList, targetClass: PsiClass, membersToMove: util.Set[PsiMember], conflicts: MultiMap[PsiElement, String]): Unit = {
    val targetName = targetClass.getName
    val memberName = scRefPattern.asInstanceOf[ScDeclaredElementsHolder].declaredNames.head
    if (targetClass.getAllMethods.map(_.getName).contains(memberName)) {
      val message = ScalaBundle.message("target.0.already.contains.definition.of.1", targetName, memberName)
      conflicts.putValue(scRefPattern, message)
    }
  }

  override def getUsage(member: PsiMember, psiReference: PsiReference, membersToMove: util.Set[PsiMember], targetClass: PsiClass): MoveMembersProcessor.MoveMembersUsageInfo = {
    psiReference.getElement match {
      case ref: ScReferenceElement =>
        ref.qualifier match {
          case Some(qualifier) => new MoveMembersProcessor.MoveMembersUsageInfo(member, ref, targetClass, qualifier, psiReference)
          case None => new MoveMembersProcessor.MoveMembersUsageInfo(member, ref, targetClass, ref, psiReference) // add qualifier
        }

      case _ => null
    }
  }

  override def changeExternalUsage(options: MoveMembersOptions, usage: MoveMembersProcessor.MoveMembersUsageInfo): Boolean = {
    val element = usage.getElement
    if (element == null || !element.isValid) return true

    usage.reference match {
      case refExpr: ScReferenceExpression =>
        refExpr.qualifier match {
          case Some(qualifier: ScReferenceExpression) =>
            changeQualifier(qualifier, usage.qualifierClass)
            addImport(qualifier, usage.qualifierClass)
            true

          case _ => false
        }

      case _ => false
    }
  }

  protected def changeQualifier(qualifier: ScReferenceExpression, targetClass: PsiClass): Unit = {
    targetClass match {
      case obj: ScTemplateDefinition =>
        qualifier.handleElementRename(obj.name)
    }

  }

  private def addImport(qualifier: ScReferenceExpression, targetClass: PsiClass): Unit = {
    qualifier.getContainingFile match {
      case file: ScalaFile =>
        file.addImportForClass(targetClass)
    }
  }

  override def getAnchor(psiMember: PsiMember, targetClass: PsiClass, set: util.Set[PsiMember]): PsiElement = {
    null
  }


  override def doMove(moveMembersOptions: MoveMembersOptions, scMember: PsiMember, anchor: PsiElement, targetClass: PsiClass): PsiMember = {
    ScalaChangeContextUtil.encodeContextInfo(Seq(scMember))
    val memberCopy = scMember.copy()
    val newMemberInTarget = targetClass.add(memberCopy)
    scMember.delete()
    newMemberInTarget.asInstanceOf[PsiMember]
  }


  override def decodeContextInfo(scope: PsiElement): Unit = ScalaChangeContextUtil.decodeContextInfo(Seq(scope))

}
