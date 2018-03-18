package org.jetbrains.plugins.scala.lang.refactoring.move.members

import java.util

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.moveMembers.{MoveJavaMemberHandler, MoveMemberHandler, MoveMembersOptions, MoveMembersProcessor}
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaChangeContextUtil
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

import scala.collection.mutable

class ScalaMoveMemberHandler extends MoveJavaMemberHandler {

  import ScalaMoveMembersHandler._

  val movedScMembers: mutable.Map[String, PsiElement] = mutable.Map.empty

  override def checkConflictsOnMember(scRefPattern: PsiMember, newVisibility: String, modifiedListCopy: PsiModifierList, targetClass: PsiClass, membersToMove: util.Set[PsiMember], conflicts: MultiMap[PsiElement, String]): Unit = {
    val targetName = targetClass.asInstanceOf[ScDeclaredElementsHolder].declaredNames.head

    Option(PsiTreeUtil.getParentOfType(scRefPattern, classOf[ScMember])) match {
      case Some(member) =>
        member.asInstanceOf[ScDeclaredElementsHolder].declaredNames.foreach(memberName =>
          if (targetClass.getAllMethods.map(_.getName).contains(memberName)) {
            val message = ScalaBundle.message("target.0.already.contains.definition.of.1", targetName, memberName)
            conflicts.putValue(member.asInstanceOf[PsiMember], message)
          })
      case None =>
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

  private def memberName(member: ScMember) = member.asInstanceOf[ScDeclaredElementsHolder].declaredNames.mkString(",")

  override def doMove(moveMembersOptions: MoveMembersOptions, scRefPattern: PsiMember, anchor: PsiElement, targetClass: PsiClass): PsiMember = {

    def findEquivalentReferencePatternFrom(newMemberInTarget: PsiElement) = {
      findReferencePatterns(newMemberInTarget).find(_.getName == scRefPattern.getName) orNull
    }

    val maybeMember = findScMember(Option(scRefPattern))

    val maybeMemberToMove = maybeMember.map(scMember =>
      if (movedScMembers.contains(memberName(scMember))) {
        movedScMembers(memberName(scMember))
      } else {
        ScalaChangeContextUtil.encodeContextInfo(Seq(scMember))
        val memberCopy = scMember.copy()
        val newMemberInTarget = targetClass.add(memberCopy)
        movedScMembers += memberName(scMember) -> newMemberInTarget
        scMember.delete()
        newMemberInTarget
      }
    )

    maybeMemberToMove.map(findEquivalentReferencePatternFrom).orNull
  }

  def init(): Unit = {
    movedScMembers.clear()
  }

  override def decodeContextInfo(scope: PsiElement): Unit = ScalaChangeContextUtil.decodeContextInfo(Seq(scope))

}

object ScalaMoveMemberHandler {
  def init(): Unit = {
    MoveMemberHandler.EP_NAME.forLanguage(ScalaLanguage.INSTANCE).asInstanceOf[ScalaMoveMemberHandler].init()
  }
}