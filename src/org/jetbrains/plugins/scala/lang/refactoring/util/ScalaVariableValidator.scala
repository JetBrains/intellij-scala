package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable.ArrayBuffer


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
    conflicts.isEmpty || conflictsReporter.reportConflicts(myProject, conflicts)
  }

  def isOKImpl(name: String, allOcc: Boolean): MultiMap[PsiElement, String] = {
    val result = MultiMap.createSet[PsiElement, String]()
    for {
      (namedElem, message) <- findConflicts(name, allOcc)
      if namedElem != selectedElement
    } {
      result.putValue(namedElem, message)
    }
    result
  }

  def findConflicts(name: String, allOcc: Boolean): Array[(PsiNamedElement, String)] = { //returns declaration and message
    val container = enclosingContainer(allOcc)
    if (container == null) return Array()
    val buf = new ArrayBuffer[(PsiNamedElement, String)]
    buf ++= validateDown(container, name, allOcc)
    buf ++= validateReference(selectedElement, name)
    var cl = container
    while (cl != null && !cl.isInstanceOf[ScTypeDefinition]) cl = cl.getParent
    if (cl != null) {
      cl match {
        case x: ScTypeDefinition =>
          for (member <- x.members) {
            member match {
              case x: ScVariable => for (el <- x.declaredElements if el.name == name)
                buf += ((el, messageForMember(el.name)))
              case x: ScValue => for (el <- x.declaredElements if el.name == name)
                buf += ((el, messageForMember(el.name)))
              case _ =>
            }
          }
          for (function <- x.functions; if function.name == name) {
            buf += ((x, messageForMember(function.name)))
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
    buf.toArray
  }

  private def validateReference(context: PsiElement, name: String): Seq[(PsiNamedElement, String)] = {
    ScalaPsiElementFactory.createExpressionFromText(name, context) match {
      case ResolvesTo(elem @ ScalaPsiUtil.inNameContext(nameCtx)) =>
        val message = nameCtx match {
          case p: ScClassParameter => messageForClassParameter(name)
          case p: ScParameter => messageForParameter(name)
          case m: ScMember if m.isLocal =>
            if (m.getTextOffset < context.getTextOffset) messageForLocal(name)
            else ""
          case _: ScCaseClause | _: ScGenerator | _: ScEnumerator => messageForLocal(name)
          case m: PsiMember => messageForMember(name)
          case _ => ""
        }
        if (message != "") Seq((elem, message))
        else Seq.empty
      case _ => Seq.empty
    }
  }

  private def validateDown(element: PsiElement, name: String, allOcc: Boolean): Seq[(PsiNamedElement, String)] = {
    val container = enclosingContainer(allOcc)
    val buf = new ArrayBuffer[(PsiNamedElement, String)]
    for (child <- element.getChildren) {
      child match {
        case x: ScClassParameter if x.name == name =>
          buf += ((x, messageForClassParameter(x.name)))
        case x: ScParameter if x.name == name =>
          buf += ((x, messageForParameter(x.name)))
        case x: ScFunctionDefinition if x.name == name =>
          buf += (if (x.isLocal) (x, messageForLocal(x.name)) else (x, messageForMember(x.name)))
        case x: ScBindingPattern if x.name == name =>
          buf += (if (x.isClassMember) (x, messageForMember(x.name)) else (x, messageForLocal(x.name)))
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
        if (PsiTreeUtil.isAncestor(container, parent, true))
          while (parent.getParent != null && parent.getParent != container) parent = parent.getParent
        else parent = container.getFirstChild
        parent
      }
      var fromDoubles = from.getPrevSibling
      var i = 0
      while (fromDoubles != null) {
        i = i + 1
        fromDoubles match {
          case x: ScVariableDefinition =>
            val elems = x.declaredElements
            for (elem <- elems; if elem.name == name)
              buf += (if (x.isLocal) (elem, messageForLocal(elem.name)) else (elem, messageForMember(elem.name)))
          case x: ScPatternDefinition =>
            val elems = x.declaredElements
            for (elem <- elems; if elem.name == name)
              buf += (if (x.isLocal) (elem, messageForLocal(elem.name)) else (elem, messageForMember(elem.name)))
          case _ =>
        }
        fromDoubles = fromDoubles.getPrevSibling
      }
      while (from != null) {
        buf ++= validateDown(from, name, allOcc)
        from = from.getNextSibling
      }
    }
    buf
  }

  def validateName(name: String, increaseNumber: Boolean): String = {
    if (noOccurrences) return name
    var res = name
    if (isOKImpl(res, allOcc = false).isEmpty) return res
    if (!increaseNumber) return ""
    var i = 1
    res = name + i
    while (!isOKImpl(res, allOcc = true).isEmpty) {
      i = i + 1
      res = name + i
    }
    res
  }

  private def messageForMember(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.field", name)
  private def messageForLocal(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.local", name)
  private def messageForParameter(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.parameter", name)
  private def messageForClassParameter(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.class.parameter", name)
}