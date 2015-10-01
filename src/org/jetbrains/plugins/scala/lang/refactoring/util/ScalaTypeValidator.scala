package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.mutable.ArrayBuffer

/**
*  Created by Kate Ustyuzhanina
*  on 8/3/15
*/
object ScalaTypeValidator {
  def apply(conflictsReporter: ConflictsReporter,
            project: Project,
            editor: Editor,
            file: PsiFile,
            element: PsiElement,
            container: PsiElement,
            noOccurrences: Boolean): ScalaTypeValidator = {
    new ScalaTypeValidator(conflictsReporter, project, element, noOccurrences, container, container)
  }


  def apply(conflictsReporter: ConflictsReporter,
            project: Project,
            editor: Editor,
            file: PsiFile,
            element: PsiElement,
            occurrences: Array[TextRange]): ScalaTypeValidator = {
    val container = ScalaRefactoringUtil.enclosingContainer(ScalaRefactoringUtil.commonParent(file, occurrences: _*))
    val containerOne = ScalaRefactoringUtil.enclosingContainer(element)
    new ScalaTypeValidator(conflictsReporter, project, element, occurrences.isEmpty, container, containerOne)
  }
}


class ScalaTypeValidator(conflictsReporter: ConflictsReporter,
                         myProject: Project,
                         selectedElement: PsiElement,
                         noOccurrences: Boolean,
                         enclosingContainerAll: PsiElement,
                         enclosingOne: PsiElement)
  extends ScalaValidator(conflictsReporter, myProject, selectedElement, noOccurrences, enclosingContainerAll, enclosingOne) {

  override def findConflicts(name: String, allOcc: Boolean): Array[(PsiNamedElement, String)] = {
    //returns declaration and message
    val container = enclosingContainer(allOcc)
    if (container == null) return Array()
    val buf = new ArrayBuffer[(PsiNamedElement, String)]

    buf ++= getForbiddenNames(container, name)

    //    val parent = container.getContext
    buf ++= getForbiddenNamesInBlock(container, name)
    buf.toArray
  }

  //TODO maybe not the best way to handle with such matching
  private def matchElement(element: PsiElement, name: String, buf: ArrayBuffer[(PsiNamedElement, String)]) = {
    element.depthFirst.forall  {
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
        buf ++= getForbiddenNamesInBlock(typeDefinition, name)
        true
      case fileType: ScalaFile =>
        buf ++= getForbiddenNamesInBlock(fileType, name)
        true
      case func: ScFunctionDefinition =>
        buf ++= getForbiddenNamesInBlock(func, name)
        true
      case funcBlock: ScBlockExpr =>
        buf ++= getForbiddenNamesInBlock(funcBlock, name)
        true
      case extendsBlock: ScExtendsBlock =>
        buf ++= getForbiddenNamesInBlock(extendsBlock, name)
        true
      case body: ScTemplateBody =>
        buf ++= getForbiddenNamesInBlock(body, name)
        true
      case expression: ScExpression =>
        buf ++= getForbiddenNamesInBlock(expression, name)
        true
      case expression: ScPackaging =>
        buf ++= getForbiddenNamesInBlock(expression, name)
        true
      case _ => true
    }
  }

  private def getForbiddenNames(position: PsiElement, name: String) = {
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

  //find conflict in ALL child from current Parent recursively
  private def getForbiddenNamesInBlock(commonParent: PsiElement, name: String): ArrayBuffer[(PsiNamedElement, String)] = {
    val buf = new ArrayBuffer[(PsiNamedElement, String)]
    for (child <- commonParent.getChildren) {
      matchElement(child, name, buf)
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

