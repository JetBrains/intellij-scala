package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable

/**
  * Created by Kate Ustyuzhanina
  * on 8/3/15
  */
class ScalaTypeValidator(override val project: Project,
                         val conflictsReporter: ConflictsReporter,
                         val selectedElement: PsiElement,
                         val noOccurrences: Boolean,
                         enclosingContainerAll: PsiElement,
                         enclosingOne: PsiElement)
  extends ScalaValidator(project, conflictsReporter, selectedElement, noOccurrences, enclosingContainerAll, enclosingOne) {

  override def findConflicts(name: String, allOcc: Boolean): Array[(PsiNamedElement, String)] = {
    //returns declaration and message
    val container = enclosingContainer(allOcc)
    if (container == null) return Array()

    forbiddenNames(container, name) match {
      case Array() => forbiddenNamesInBlock(container, name)
      case array => array
    }
  }

  import ScalaTypeValidator._

  protected def forbiddenNames(position: PsiElement, name: String): Array[(PsiNamedElement, String)] = {
    val result = mutable.ArrayBuffer.empty[(PsiNamedElement, String)]

    implicit val typeSystem = project.typeSystem
    val processor = new BaseProcessor(ValueSet(ResolveTargets.CLASS)) {
      override def execute(element: PsiElement, state: ResolveState): Boolean = {
        result ++= zipWithMessage(element, name)
        true
      }
    }
    PsiTreeUtil.treeWalkUp(processor, position, null, ResolveState.initial())

    result.toArray
  }

  protected def forbiddenNamesInBlock(commonParent: PsiElement, name: String): Array[(PsiNamedElement, String)] = {
    val result = mutable.ArrayBuffer.empty[(PsiNamedElement, String)]

    commonParent.depthFirst().foreach {
      result ++= zipWithMessage(_, name)
    }

    result.toArray
  }

  override def validateName(name: String): String =
    super.validateName(name.capitalize)
}

object ScalaTypeValidator {

  def empty(project: Project): ScalaTypeValidator =
    new ScalaTypeValidator(project, null, null, noOccurrences = true, null, null) {
      override def validateName(name: String): String = name
    }

  def apply(project: Project,
            conflictsReporter: ConflictsReporter,
            element: PsiElement,
            container: PsiElement,
            noOccurrences: Boolean) =
    new ScalaTypeValidator(project, conflictsReporter, element, noOccurrences, container, container)

  private def zipWithMessage(element: PsiElement, name: String): Option[(PsiNamedElement, String)] =
    Option(element).collect {
      case named: PsiNamedElement if named.getName == name => named
    }.collect {
      case named@(_: ScTypeAlias | _: ScTypeParam) => (named, "type")
      case typeDefinition: ScTypeDefinition if getParentOfType(typeDefinition, classOf[ScFunctionDefinition]) == null =>
        (typeDefinition, "class")
    }.map {
      case (named, kind) => (named, message(kind, name))
    }

  private[this] def message(kind: String, name: String) =
    ScalaBundle.message(s"introduced.typeAlias.will.conflict.with.$kind.name", name)
}
