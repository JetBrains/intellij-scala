package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle

import scala.collection.mutable.ArrayBuffer

/**
 * Created by Kate Ustyuzhanina on 8/25/15.
 */
object ScalaCompositeValidator {
  def apply(validators: List[ScalaValidator],
            conflictsReporter: ConflictsReporter,
            myProject: Project,
            selectedElement: PsiElement,
            noOccurrences: Boolean,
            enclosingContainerAll: PsiElement,
            enclosingOne: PsiElement): ScalaCompositeValidator = {
    new ScalaCompositeValidator(conflictsReporter, myProject,
      selectedElement, noOccurrences, enclosingContainerAll, enclosingOne, validators)
  }
}


class ScalaCompositeValidator(conflictsReporter: ConflictsReporter,
                              myProject: Project,
                              selectedElement: PsiElement,
                              noOccurrences: Boolean,
                              enclosingContainerAll: PsiElement,
                              enclosingOne: PsiElement,
                              validators: List[ScalaValidator])
  extends ScalaValidator(conflictsReporter, myProject, selectedElement, noOccurrences, enclosingContainerAll, enclosingOne) {

  override def findConflicts(name: String, allOcc: Boolean): Array[(PsiNamedElement, String)] = {
    //returns declaration and message
    val buf = new ArrayBuffer[(PsiNamedElement, String)]
    for (validator <- validators) {
      buf ++= validator.findConflicts(name, allOcc)
    }

    buf.toArray
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

