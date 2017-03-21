package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier

/**
  * @author Kate Ustyuzhanina
  */
abstract class ScalaValidator(val project: Project,
                              conflictsReporter: ConflictsReporter,
                              selectedElement: PsiElement,
                              noOccurrences: Boolean,
                              enclosingContainerAll: PsiElement,
                              enclosingOne: PsiElement) {

  def enclosingContainer(allOcc: Boolean): PsiElement =
    if (allOcc) enclosingContainerAll else enclosingOne

  def isOK(dialog: NamedDialog): Boolean = isOK(dialog.getEnteredName, dialog.isReplaceAllOccurrences)

  def isOK(newName: String, replaceAllOccurrences: Boolean): Boolean = {
    if (noOccurrences) return true
    findConflicts(newName, replaceAllOccurrences) match {
      case Seq() => true
      case conflicts => conflictsReporter.reportConflicts(project, conflicts)
    }
  }

  final def findConflicts(name: String, allOccurrences: Boolean): Seq[(PsiNamedElement, String)] =
    findConflictsImpl(name, allOccurrences).filter {
      case (namedElement, _) => namedElement != selectedElement
    }

  protected def findConflictsImpl(name: String, allOccurrences: Boolean): Seq[(PsiNamedElement, String)]

  def validateName(name: String): String = {
    if (noOccurrences) return name
    var res = name
    if (findConflicts(res, allOccurrences = false).isEmpty) return res

    var i = 1
    res = name + i
    if (!isIdentifier(res)) {
      res = name + name.last
      while (findConflicts(res, allOccurrences = true).nonEmpty) {
        res = name + name.last
      }
    } else {
      while (findConflicts(res, allOccurrences = true).nonEmpty) {
        i = i + 1
        res = name + i
      }
    }
    res
  }

}
