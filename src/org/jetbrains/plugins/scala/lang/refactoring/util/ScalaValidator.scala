package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.containers.MultiMap
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

  def isOK(newName: String, isReplaceAllOcc: Boolean): Boolean = {
    if (noOccurrences) return true
    val conflicts = isOKImpl(newName, isReplaceAllOcc)
    conflicts.isEmpty || conflictsReporter.reportConflicts(project, conflicts)
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

  def findConflicts(name: String, allOcc: Boolean): Array[(PsiNamedElement, String)]

  def validateName(name: String, increaseNumber: Boolean): String = {
    if (noOccurrences) return name
    var res = name
    if (isOKImpl(res, allOcc = false).isEmpty) return res
    if (!increaseNumber) return ""
    var i = 1
    res = name + i
    if (!isIdentifier(res)) {
      res = name + name.last
      while (!isOKImpl(res, allOcc = true).isEmpty) {
        res = name + name.last
      }
    } else {
      while (!isOKImpl(res, allOcc = true).isEmpty) {
        i = i + 1
        res = name + i
      }
    }
    res
  }

}
