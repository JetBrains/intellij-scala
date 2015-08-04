package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScClassParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
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
                         enclosingOne: PsiElement) extends NameValidator {

  def getProject(): Project = {
    myProject
  }

  def enclosingContainer(allOcc: Boolean): PsiElement =
    if (allOcc) enclosingContainerAll else enclosingOne

  def isOK(dialog: NamedDialog): Boolean = isOK(dialog.getEnteredName, dialog.isReplaceAllOccurrences)

  private def isOK(newName: String, isReplaceAllOcc: Boolean): Boolean = {
    if (noOccurrences) return true
    val conflicts = isOKImpl(newName, isReplaceAllOcc)
    conflicts.isEmpty || conflictsReporter.reportConflicts(myProject, conflicts)
  }

  //validator use upperCase of a given name
  private def isOKImpl(name: String, allOcc: Boolean): MultiMap[PsiElement, String] = {
    val result = MultiMap.createSet[PsiElement, String]()
    for {
      (namedElem, message) <- findConflicts(name, allOcc)
      if namedElem != selectedElement
    } {
      result.putValue(namedElem, message)
    }
    result
  }

  private def findConflicts(name: String, allOcc: Boolean): Array[(PsiNamedElement, String)] = {
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

  def validateName(name: String, increaseNumber: Boolean): String = {
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

