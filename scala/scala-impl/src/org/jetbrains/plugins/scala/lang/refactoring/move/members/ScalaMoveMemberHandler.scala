package org.jetbrains.plugins.scala.lang.refactoring.move.members

import com.intellij.psi._
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor.MoveMembersUsageInfo
import com.intellij.refactoring.move.moveMembers.{MoveJavaMemberHandler, MoveMembersOptions}
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createExpressionWithContextFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.refactoring.Associations
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaChangeContextUtil

import java.{util => ju}

final class ScalaMoveMemberHandler extends MoveJavaMemberHandler {

  override def checkConflictsOnMember(member: PsiMember,
                                      newVisibility: String,
                                      modifiedListCopy: PsiModifierList,
                                      targetClass: PsiClass,
                                      membersToMove: ju.Set[PsiMember],
                                      conflicts: MultiMap[PsiElement, String]): Unit = {
    val targetName = targetClass.name
    for {
      nameInTarget <- targetClass.getAllMethods.map(_.name)
      movedName <- member.names
      if nameInTarget == movedName
    } {
      val message = ScalaBundle.message("target.0.already.contains.definition.of.1", targetName, movedName)
      conflicts.putValue(member, message)
    }
  }

  override def getUsage(member: PsiMember,
                        psiReference: PsiReference,
                        membersToMove: ju.Set[PsiMember],
                        targetClass: PsiClass): MoveMembersUsageInfo =
    psiReference.getElement match {
      case ref: ScReference =>
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

  override def changeExternalUsage(options: MoveMembersOptions, usage: MoveMembersUsageInfo): Boolean = {
    val element = usage.getElement
    if (element == null || !element.isValid) return true

    usage.reference match {
      case ref@ScReference.qualifier(qualifier: ScReference) =>
        if (usage.qualifierClass != null)
          changeQualifier(qualifier, usage.qualifierClass)
        else
          removeQualifier(ref, qualifier)
      case ref: ScReference if usage.qualifierClass != null =>
        addQualifier(ref, usage.qualifierClass)
      case _ =>
        true
    }
  }

  private def removeQualifier(ref: ScReference, qualifier: ScReference): Boolean = {
    ref.getParent match {
      case importExpr: ScImportExpr => importExpr.deleteExpr()
      case importSelector: ScImportSelector => importSelector.deleteSelector(removeRedundantBraces = true)
      case _ =>
        val identifier = ref.nameId
        val beforeId = identifier.getPrevSibling
        ref.deleteChildRange(qualifier, beforeId)
    }
    true
  }

  private def changeQualifier(qualifier: ScReference, targetClass: PsiClass): Boolean = {
    val newReference = createExpressionWithContextFromText(targetClass.name, qualifier.getContext, qualifier)
    val newQualifier = qualifier.replace(newReference)
    newQualifier.asInstanceOf[ScReferenceExpression].bindToElement(targetClass)
    true
  }

  private def addQualifier(reference: ScReference, targetClass: PsiClass): Boolean = {
    import reference.projectContext

    val textWithQualifier = s"${targetClass.name}.${reference.refName}"
    val qualified = reference match {
      case _: ScReferenceExpression =>
        createExpressionFromText(textWithQualifier, reference)
      case _: ScStableCodeReference =>
        createReferenceFromText(textWithQualifier)
      case _ => return false
    }

    reference.replace(qualified) match {
      case ScReference.qualifier(q: ScReference) =>
        q.bindToElement(targetClass)
      case _ =>
    }
    true
  }

  import ScalaChangeContextUtil._

  override def getAnchor(psiMember: PsiMember,
                         targetClass: PsiClass,
                         set: ju.Set[PsiMember]): PsiElement = null

  override def doMove(moveMembersOptions: MoveMembersOptions,
                      scMember: PsiMember,
                      anchor: PsiElement,
                      targetClass: PsiClass): PsiMember = {
    val associations = collectDataForElement(scMember)
    val memberCopy = scMember.copy()

    val movedMember = (targetClass, memberCopy) match {
      case (td: ScTemplateDefinition, m: ScMember) =>
        td.addMember(m, None)
      case _ =>
        targetClass.add(memberCopy)
    }

    Associations.Data(movedMember) = associations
    MovedElementData(targetClass) = movedMember

    scMember.delete()

    movedMember.asInstanceOf[PsiMember]
  }

  override def decodeContextInfo(targetClass: PsiElement): Unit = {
    val movedElement = movedMember(targetClass)
    Associations.restoreFor(movedElement)
  }

}
