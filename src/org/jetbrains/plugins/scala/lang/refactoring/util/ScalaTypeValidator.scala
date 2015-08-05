package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.mutable.ArrayBuffer

/**
 * Created by Kate Ustyuzhanina on 8/3/15.
 */
object ScalaTypeValidator {
  def apply(conflictsReporter: ConflictsReporter,
            project: Project,
            editor: Editor,
            file: PsiFile,
            element: PsiElement,
            occurrences: Array[ScTypeElement]): ScalaTypeValidator = {
    val container = ScalaRefactoringUtil.enclosingContainer(PsiTreeUtil.findCommonParent(occurrences: _*))
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
    getForbiddenNames(container, name).toArray
  }


  private def getForbiddenNames(position: PsiElement, name: String) = {
    class FindTypeAliasProcessor extends BaseProcessor(ValueSet(ResolveTargets.CLASS)) {
      val buf = new ArrayBuffer[(PsiNamedElement, String)]

      override def execute(element: PsiElement, state: ResolveState): Boolean = {
        element match {
          case typeAlias: ScTypeAliasDefinition if typeAlias.getName == name =>
            buf += (if (typeAlias.isLocal)
              (typeAlias, messageForLocal(typeAlias.getName)) else (typeAlias, messageForMember(typeAlias.getName)))
            true
          case clazz: ScClass if clazz.getName == name =>
            buf += (if (clazz.isLocal)
              (clazz, messageForLocal(clazz.getName)) else (clazz, messageForMember(clazz.getName)))
            true
          case _ => true
        }
      }
    }

    val processor = new FindTypeAliasProcessor
    PsiTreeUtil.treeWalkUp(processor, position, null, ResolveState.initial())
    processor.buf
  }

  override def validateName(name: String, increaseNumber: Boolean): String = {
    val newName = name.toUpperCase
    if (noOccurrences) return newName
    var res = newName
    if (isOKImpl(res, allOcc = false).isEmpty) return res
    if (!increaseNumber) return ""
    var i = 1
    res = newName + i
    while (!isOKImpl(res, allOcc = true).isEmpty) {
      i = i + 1
      res = newName + i
    }
    res
  }

  private def messageForMember(name: String) = ScalaBundle.message("introduced.typealias.will.conflict.with.global", name)

  private def messageForLocal(name: String) = ScalaBundle.message("introduced.typealias.will.conflict.with.local", name)
}

