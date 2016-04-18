package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable.ArrayBuffer

/**
 * Created by Kate Ustyuzhanina
 * on 8/3/15
 */
object ScalaTypeValidator {
  def apply(conflictsReporter: ConflictsReporter,
            project: Project,
            element: PsiElement,
            container: PsiElement,
            noOccurrences: Boolean): ScalaTypeValidator = {
    new ScalaTypeValidator(conflictsReporter, project, element, noOccurrences, container, container)
  }
}


class ScalaTypeValidator(val conflictsReporter: ConflictsReporter,
                         val myProject: Project,
                         val selectedElement: PsiElement,
                         val noOccurrences: Boolean,
                         val enclosingContainerAll: PsiElement,
                         val enclosingOne: PsiElement)
  extends ScalaValidator(conflictsReporter, myProject, selectedElement, noOccurrences, enclosingContainerAll, enclosingOne) {
  private implicit val typeSystem = myProject.typeSystem

  override def findConflicts(name: String, allOcc: Boolean): Array[(PsiNamedElement, String)] = {
    //returns declaration and message
    val container = enclosingContainer(allOcc)
    if (container == null) return Array()
    val buf = new ArrayBuffer[(PsiNamedElement, String)]

    buf ++= getForbiddenNames(container, name)

    if (buf.isEmpty) {
      buf ++= getForbiddenNamesInBlock(container, name)
    }

    buf.toArray
  }

  def getForbiddenNames(position: PsiElement, name: String) = {
    class FindTypeAliasProcessor extends BaseProcessor(ValueSet(ResolveTargets.CLASS)) {
      val buf = new ArrayBuffer[(PsiNamedElement, String)]

      override def execute(element: PsiElement, state: ResolveState): Boolean = {
        element match {
          case typeAlias: ScTypeAlias if typeAlias.getName == name =>
            buf += ((typeAlias, messageForTypeAliasMember(name)))
            true
          case typeParametr: ScTypeParam if typeParametr.getName == name =>
            buf += ((typeParametr, messageForTypeAliasMember(name)))
            true
          case typeDefinition: ScTypeDefinition =>
            if (typeDefinition.getName == name) {
              buf += ((typeDefinition, messageForClassMember(name)))
            }
            true
          case _ => true
        }
      }
    }

    val processor = new FindTypeAliasProcessor
    PsiTreeUtil.treeWalkUp(processor, position, null, ResolveState.initial())
    processor.buf
  }

  def getForbiddenNamesInBlock(commonParent: PsiElement, name: String): ArrayBuffer[(PsiNamedElement, String)] = {
    val buf = new ArrayBuffer[(PsiNamedElement, String)]
    commonParent.depthFirst.foreach {
      case typeAlias: ScTypeAlias if typeAlias.getName == name =>
        buf += ((typeAlias, messageForTypeAliasMember(name)))
        true
      case typeParametr: ScTypeParam if typeParametr.getName == name =>
        buf += ((typeParametr, messageForTypeAliasMember(name)))
        true
      case typeDefinition: ScTypeDefinition =>
        if ((typeDefinition.getName == name) &&
          (PsiTreeUtil.getParentOfType(typeDefinition, classOf[ScFunctionDefinition]) == null)) {
          buf += ((typeDefinition, messageForClassMember(name)))
        }
        true
      case _ => true
    }
    buf
  }

  override def validateName(name: String, increaseNumber: Boolean): String = {
    val newName = name.capitalize
    super.validateName(newName, increaseNumber)
  }

  private def messageForTypeAliasMember(name: String) =
    ScalaBundle.message("introduced.typealias.will.conflict.with.type.name", name)

  private def messageForClassMember(name: String) =
    ScalaBundle.message("introduced.typealias.will.conflict.with.class.name", name)
}