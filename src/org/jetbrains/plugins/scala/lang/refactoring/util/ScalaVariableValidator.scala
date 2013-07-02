package org.jetbrains.plugins.scala.lang.refactoring.util

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScClass}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameters, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement


/**
* User: Alexander Podkhalyuzin
* Date: 24.06.2008
*/

object ScalaVariableValidator {
  def apply(conflictsReporter: ConflictsReporter,
            project: Project,
            editor: Editor,
            file: PsiFile,
            mainOccurence: TextRange,
            occurrences: Array[TextRange]): ScalaVariableValidator = {
    val container = ScalaRefactoringUtil.enclosingContainer(file, occurrences: _*)
    val containerOne = ScalaRefactoringUtil.enclosingContainer(file, mainOccurence)
    ScalaRefactoringUtil.getExpression(project, editor, file, mainOccurence.getStartOffset, mainOccurence.getEndOffset) match {
      case Some((expr, _)) => new ScalaVariableValidator(conflictsReporter, project, expr, occurrences.isEmpty, container, containerOne)
      case _ => null
    }
  }

  def apply(conflictsReporter: ConflictsReporter,
            project: Project,
            editor: Editor,
            file: PsiFile,
            element: PsiElement,
            occurrences: Array[TextRange]): ScalaVariableValidator = {
    val container = ScalaRefactoringUtil.enclosingContainer(file, occurrences: _*)
    val containerOne = ScalaRefactoringUtil.enclosingContainer(file, element.getTextRange)
    new ScalaVariableValidator(conflictsReporter, project, element, occurrences.isEmpty, container, containerOne)
  }
}

class ScalaVariableValidator(conflictsReporter: ConflictsReporter,
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

  def isOK(newName: String, isReplaceAllOcc: Boolean): Boolean = {
    if (noOccurrences) return true
    val conflicts = isOKImpl(newName, isReplaceAllOcc)
    conflicts.length == 0 || conflictsReporter.reportConflicts(conflicts, myProject)
  }

  def isOKImpl(name: String, allOcc: Boolean): Array[String] =
    findConflicts(name, allOcc).
            toSet.filter(_._1 != selectedElement).
            map(_._2).toArray

  def findConflicts(name: String, allOcc: Boolean): Array[(ScNamedElement, String)] = { //returns declaration and message
    val container = enclosingContainer(allOcc)
    if (container == null) return Array()
    val buf = new ArrayBuffer[(ScNamedElement, String)]
    buf ++= validateDown(container, name, allOcc)
    buf ++= validateUp(container, name)
    var cl = container
    while (cl != null && !cl.isInstanceOf[ScTypeDefinition]) cl = cl.getParent
    if (cl != null) {
      cl match {
        case x: ScTypeDefinition => {
          for (member <- x.members) {
            member match {
              case x: ScVariable => for (el <- x.declaredElements if el.name == name)
                buf += ((el, messageForField(el.name)))
              case x: ScValue => for (el <- x.declaredElements if el.name == name)
                buf += ((el, messageForField(el.name)))
              case _ =>
            }
          }
          for (function <- x.functions; if function.name == name) {
            buf += ((x, messageForField(function.name)))
          }
          x match {
            case scClass: ScClass =>
              for {
                constructor <- scClass.constructor
                parameter <- constructor.parameters
                if parameter.name == name
              } {
                buf += ((parameter, messageForClassParameter(parameter.name)))
              }
            case _ =>
          }
        }
      }
    }
    buf.toArray
  }

  private def validateUp(element: PsiElement, name: String): Array[(ScNamedElement, String)] = {
    val buf = new ArrayBuffer[(ScNamedElement, String)]
    val parent = if (element.getPrevSibling != null) element.getPrevSibling else element.getParent
    element match {
      case x: ScVariableDefinition => {
        val elems = x.declaredElements
        for (elem <- elems) {
          if (elem.name == name) {
            buf += ((elem, messageForLocal(elem.name)))
          }
        }
      }
      case x: ScPatternDefinition => {
        val elems = x.declaredElements
        for (elem <- elems) {
          if (elem.name == name) {
            buf += ((elem, messageForLocal(elem.name)))
          }
        }
      }
      case x: ScParameters => {
        for (parameter <- x.params)
        if (parameter.name == name) {
          buf += ((parameter, messageForParameter(parameter.name)))
        }
      }
      case x: ScFunctionDefinition => {
        if (x.name == name) {
          buf += ((x, messageForLocal(x.name)))
        }
      }
      case _ =>
    }
    parent match {
      case _: ScTemplateBody | null =>
      case _ => parent.getParent match {
        case _: ScTemplateBody =>
        case _ => buf ++= validateUp(parent, name)
      }
    }
    buf.toArray
  }

  private def validateDown(element: PsiElement, name: String, allOcc: Boolean): Array[(ScNamedElement, String)] = {
    val container = enclosingContainer(allOcc)
    val buf = new ArrayBuffer[(ScNamedElement, String)]
    for (child <- element.getChildren) {
      child match {
        case x: ScClassParameter if x.name == name =>
          buf += ((x, messageForClassParameter(x.name)))
        case x: ScParameter if x.name == name =>
          buf += ((x, messageForParameter(x.name)))
        case x: ScFunctionDefinition if x.name == name =>
          buf += (if (x.isLocal) (x, messageForLocal(x.name)) else (x, messageForField(x.name)))
        case x: ScBindingPattern if x.name == name =>
          buf += (if (x.isClassMember) (x, messageForField(x.name)) else (x, messageForLocal(x.name)))
        case _ =>
      }
    }
    if (element != container)
      for (child <- element.getChildren) {
        buf ++= validateDown(child, name, allOcc)
      }
    else {
      var from = {
        var parent: PsiElement = if (allOcc) {
          selectedElement //todo:
        } else {
          selectedElement
        }
        if (parent != container)
          while (parent.getParent != null && parent.getParent != container) parent = parent.getParent
        else parent = parent.getFirstChild
        parent
      }
      var fromDoubles = from.getPrevSibling
      var i = 0
      while (fromDoubles != null) {
        i = i + 1
        fromDoubles match {
          case x: ScVariableDefinition => {
            val elems = x.declaredElements
            for (elem <- elems; if elem.name == name)
              buf += (if (x.isLocal) (elem, messageForLocal(elem.name)) else (elem, messageForField(elem.name)))
          }
          case x: ScPatternDefinition => {
            val elems = x.declaredElements
            for (elem <- elems; if elem.name == name)
              buf += (if (x.isLocal) (elem, messageForLocal(elem.name)) else (elem, messageForField(elem.name)))
          }
          case _ =>
        }
        fromDoubles = fromDoubles.getPrevSibling
      }
      while (from != null) {
        buf ++= validateDown(from, name, allOcc)
        from = from.getNextSibling
      }
    }
    buf.toArray
  }

  def validateName(name: String, increaseNumber: Boolean): String = {
    if (noOccurrences) return name
    var res = name
    if (isOKImpl(res, allOcc = false).length == 0) return res
    if (!increaseNumber) return ""
    var i = 1
    res = name + i
    while (!(isOKImpl(res, allOcc = true).length == 0)) {
      i = i + 1
      res = name + i
    }
    res
  }

  private def messageForField(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.field", name)
  private def messageForLocal(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.local", name)
  private def messageForParameter(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.parameter", name)
  private def messageForClassParameter(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.class.parameter", name)
}