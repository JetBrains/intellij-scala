package org.jetbrains.plugins.scala.lang.refactoring.move.members

import java.util

import com.intellij.psi._
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor.MoveMembersUsageInfo
import com.intellij.refactoring.move.moveMembers.{MoveJavaMemberHandler, MoveMembersOptions}
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createReferenceFromText}
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

  override def getUsage(member: PsiMember, psiReference: PsiReference, membersToMove: util.Set[PsiMember], targetClass: PsiClass): MoveMembersUsageInfo = {
    psiReference.getElement match {
      case ref: ScReferenceElement =>
        ref.qualifier match {
          case Some(qualifier) if targetClass.isAncestorOf(ref) =>
            new MoveMembersUsageInfo(member, ref, null, qualifier, psiReference) //remove qualifier for refs in target class
          case None if member.containingClass.isAncestorOf(ref) =>
            new MoveMembersUsageInfo(member, ref, targetClass, ref, psiReference) //add qualifier, if in source class
          case Some(qualifier) =>
            new MoveMembersUsageInfo(member, ref, targetClass, qualifier, psiReference) //change qualifier
          case None =>
            new MoveMembersUsageInfo(member, ref, null, ref, psiReference)
        }

      case _ => null
    }
  }

  override def changeExternalUsage(options: MoveMembersOptions, usage: MoveMembersUsageInfo): Boolean = {
    val element = usage.getElement
    if (element == null || !element.isValid) return true

    usage.reference match {
      case ref @ ScReferenceElement.withQualifier(qualifier: ScReferenceElement) =>
        if (usage.qualifierClass != null)
          changeQualifier(qualifier, usage.qualifierClass)
        else
          removeQualifier(ref, qualifier)
      case ref: ScReferenceElement if usage.qualifierClass != null =>
        addQualifier(ref, usage.qualifierClass)
      case _ => false
    }
  }

  private def removeQualifier(ref: ScReferenceElement, qualifier: ScReferenceElement): Boolean = {
    ref.getParent match {
      case importExpr: ScImportExpr => importExpr.deleteExpr()
      case importSelector: ScImportSelector => importSelector.deleteSelector()
      case _ =>
        val identifier = ref.nameId
        val beforeId = identifier.getPrevSibling
        ref.deleteChildRange(qualifier, beforeId)
    }
    true
  }

  private def changeQualifier(qualifier: ScReferenceElement, targetClass: PsiClass): Boolean = {
    qualifier.handleElementRename(targetClass.name)
    qualifier.bindToElement(targetClass)
    true
  }

  private def addQualifier(reference: ScReferenceElement, targetClass: PsiClass): Boolean = {
    import reference.projectContext

    val textWithQualifier = s"${targetClass.name}.${reference.refName}"
    val qualified = reference match {
      case _: ScReferenceExpression =>
        createExpressionFromText(textWithQualifier)
      case _: ScStableCodeReferenceElement =>
        createReferenceFromText(textWithQualifier)
      case _ => return false
    }

    reference.replace(qualified) match {
      case ScReferenceElement.withQualifier(q: ScReferenceElement) =>
        q.bindToElement(targetClass)
      case _ =>
    }
    true
  }

  override def getAnchor(psiMember: PsiMember, targetClass: PsiClass, set: util.Set[PsiMember]): PsiElement = {
    null
  }

  override def doMove(moveMembersOptions: MoveMembersOptions, scMember: PsiMember, anchor: PsiElement, targetClass: PsiClass): PsiMember = {
    val associations = ScalaChangeContextUtil.collectDataForElement(scMember)
    val memberCopy = scMember.copy()

    val movedMember = targetClass.add(memberCopy)

    ScalaChangeContextUtil.storeContextInfo(associations, movedMember)
    ScalaChangeContextUtil.storeMovedMember(movedMember, targetClass)

    scMember.delete()

    movedMember.asInstanceOf[PsiMember]
  }

  override def decodeContextInfo(targetClass: PsiElement): Unit = {
    val movedMember =
      ScalaChangeContextUtil.getMovedMember(targetClass)

    ScalaChangeContextUtil.restoreForElement(movedMember)
  }

}
