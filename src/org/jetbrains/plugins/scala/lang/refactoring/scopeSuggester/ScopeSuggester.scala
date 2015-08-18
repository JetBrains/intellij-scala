package org.jetbrains.plugins.scala
package lang
package refactoring
package scopeSuggester

import java.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.{ConflictsReporter, ScalaRefactoringUtil, ScalaTypeValidator, ScalaValidator}


/**
 * Created by Kate Ustyuzhanina on 8/12/15.
 */
object ScopeSuggester {
  def suggestScopes(conflictsReporter: ConflictsReporter,
                    project: Project,
                    editor: Editor,
                    file: PsiFile,
                    curerntElement: ScTypeElement): util.ArrayList[ScopeItem] = {

    def getParent(element: PsiElement, isScriptFile: Boolean): PsiElement = {
      if (isScriptFile)
        PsiTreeUtil.getParentOfType(element, classOf[ScTemplateBody], classOf[ScalaFile])
      else
        PsiTreeUtil.getParentOfType(element, classOf[ScTemplateBody])
    }

    val isScriptFile = curerntElement.getContainingFile.asInstanceOf[ScalaFile].isScriptFile()
    var parent = getParent(curerntElement, isScriptFile)

    var result: List[ScopeItem] = List()
    while (parent != null) {
      var occInCompanionObj: Array[ScTypeElement] = Array[ScTypeElement]()
      val name = parent match {
        case fileType: ScalaFile => "file " + fileType.getName
        case _ =>
          PsiTreeUtil.getParentOfType(parent, classOf[ScTemplateDefinition]) match {
            case classType: ScClass =>
              "class " + classType.getName
            case objectType: ScObject =>
              occInCompanionObj = getOccurrencesFromCompanionObject(curerntElement, objectType)
              "object " + objectType.getName.substring(0, objectType.getName.length - 1)
            case traitType: ScTrait =>
              "trait " + traitType.getName
          }
      }

      val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(curerntElement, parent)
      val validator = ScalaTypeValidator(conflictsReporter, project, editor, file, curerntElement, parent, occurrences.isEmpty)
      val possibleNames = NameSuggester.namesByType(curerntElement.calcType)(validator)

      result = result :+ new ScopeItem(name, parent, occurrences, occInCompanionObj, validator, possibleNames.toList.reverse.toArray)
      parent = getParent(parent, isScriptFile)
    }
    import scala.collection.JavaConversions.asJavaCollection
    new util.ArrayList[ScopeItem](result.toIterable)
  }

  private def getOccurrencesFromCompanionObject(typeElement: ScTypeElement,
                                                objectType: ScObject): Array[ScTypeElement] = {
    val parent: PsiElement = objectType.getParent
    val name = objectType.getName.substring(0, objectType.getName.length - 1)
    val companion = parent.getChildren.find({
      case classType: ScClass if classType.getName == name =>
        true
      case traitType: ScTrait if traitType.getName == name =>
        true
      case _ => false
    })

    if (companion.isDefined)
      ScalaRefactoringUtil.getTypeElementOccurrences(typeElement, companion.get)
    else
      Array[ScTypeElement]()
  }
}

class ScopeItem(name: String,
                encloser: PsiElement,
                inOccurrences: Array[ScTypeElement],
                inOccInCompanionObj: Array[ScTypeElement],
                inValidator: ScalaValidator,
                inAvailablenames: Array[String]) {
  val fileEncloser: PsiElement = encloser
  val scopeName: String = name
  val occurrences: Array[ScTypeElement] = inOccurrences
  val occInCompanionObj: Array[ScTypeElement] = inOccInCompanionObj
  val validator: ScalaValidator = inValidator
  val possibleNames: Array[String] = inAvailablenames

  override def toString: String = name
}
