package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier

class ValidationReporter(project: Project, conflictsReporter: ConflictsReporter, validator: ScalaValidator) {
  def isOK(dialog: NamedDialog): Boolean =
    isOK(dialog.getEnteredName, dialog.isReplaceAllOccurrences)

  def isOK(newName: String, replaceAllOccurrences: Boolean): Boolean = {
    if (validator.noOccurrences) return true
    validator.findConflicts(newName, replaceAllOccurrences) match {
      case Seq() => true
      case conflicts => conflictsReporter.reportConflicts(project, conflicts)
    }
  }
}

abstract class ScalaValidator(selectedElement: PsiElement,
                              val noOccurrences: Boolean,
                              enclosingContainerAll: PsiElement,
                              enclosingOne: PsiElement) {

  def enclosingContainer(allOccurrences: Boolean): PsiElement =
    if (allOccurrences) enclosingContainerAll else enclosingOne

  final def findConflicts(name: String, allOccurrences: Boolean): Seq[(PsiNamedElement, String)] =
    findConflictsImpl(name, allOccurrences).filter {
      case (namedElement, _) => namedElement != selectedElement
    }

  protected def findConflictsImpl(name: String, allOccurrences: Boolean): Seq[(PsiNamedElement, String)]

  def validateName(name: String): String = {
    if (noOccurrences) return name
    var result = name
    if (findConflicts(result, allOccurrences = false).isEmpty) return result

    var i = 1
    result = name + i
    if (!isIdentifier(result)) {
      result = name + name.last
      while (findConflicts(result, allOccurrences = true).nonEmpty) {
        result = name + name.last
      }
    } else {
      while (findConflicts(result, allOccurrences = true).nonEmpty) {
        i = i + 1
        result = name + i
      }
    }
    result
  }

}
