package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, GlobalSearchScopesCore, PsiSearchHelper}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.types.{ScProjectionType, ScTypeParameterType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util._
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


/**
 * Created by Kate Ustyuzhanina
 * on 8/12/15
 */
object ScopeSuggester {
  def suggestScopes(conflictsReporter: ConflictsReporter,
                    project: Project,
                    editor: Editor,
                    file: PsiFile,
                    currentElement: ScTypeElement): Array[ScopeItem] = {

    def getParent(element: PsiElement, isScriptFile: Boolean): PsiElement = {
      if (isScriptFile)
        PsiTreeUtil.getParentOfType(element, classOf[ScTemplateBody], classOf[ScalaFile])
      else
        PsiTreeUtil.getParentOfType(element, classOf[ScTemplateBody])
    }

    val isScriptFile = currentElement.getContainingFile.asInstanceOf[ScalaFile].isScriptFile()

    val isContainTypeParameter = ScalaRefactoringUtil.isTypeContainsTypeParameter(currentElement)
    val elementOwner = PsiTreeUtil.getParentOfType(currentElement, classOf[ScTypeParametersOwner], true)
    val isTypeAlias = ScalaRefactoringUtil.isTypeAlias(currentElement)

    var parent = getParent(currentElement, isScriptFile)

    //forbid to work with no template definition level
    var noContinue = if ((isContainTypeParameter || isTypeAlias) && !elementOwner.isInstanceOf[ScTemplateDefinition]) {
      true
    } else {
      false
    }

    val result: ArrayBuffer[ScopeItem] = new ArrayBuffer[ScopeItem]()
    while (parent != null && !noContinue) {
      var occInCompanionObj: Array[ScTypeElement] = Array[ScTypeElement]()
      val name = parent match {
        case fileType: ScalaFile => "file " + fileType.getName
        case _ =>
          PsiTreeUtil.getParentOfType(parent, classOf[ScTemplateDefinition]) match {
            case classType: ScClass =>
              "class " + classType.name
            case objectType: ScObject =>
              occInCompanionObj = getOccurrencesFromCompanionObject(currentElement, objectType)
              "object " + objectType.name
            case traitType: ScTrait =>
              "trait " + traitType.name
          }
      }

      //parent != null here
      //check can we use upper scope
      noContinue = currentElement.calcType match {
        case projectionType: ScProjectionType
          //we can't use typeAlias outside scope where it was defined
          if parent.asInstanceOf[ScTemplateBody].isAncestorOf(projectionType.actualElement) =>
          true
        case _ => false
      }

      if (isContainTypeParameter && elementOwner.isAncestorOf(parent)) {
        noContinue = true
      }

      val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(currentElement, parent)
      val validator = ScalaTypeValidator(conflictsReporter, project, editor, file, currentElement, parent, occurrences.isEmpty)

      val possibleNames = NameSuggester.suggestNamesByType(currentElement.calcType)
        .map((value:String) => validator.validateName(value, increaseNumber = true))

      val scope = new ScopeItem(name, parent, occurrences, occInCompanionObj, validator, possibleNames.toArray)
      result += scope
      parent = getParent(parent, isScriptFile)
    }

    val scPackage = PsiTreeUtil.getParentOfType(currentElement, classOf[ScPackaging])

    //forbid to use typeParameter type outside the class
    if ((scPackage != null) && !currentElement.calcType.isInstanceOf[ScTypeParameterType] && !noContinue) {
      val allPackages = getAllAvailablePackages(scPackage.fullPackageName, currentElement)
      for (helpp <- allPackages) {
        //        result += handleOnePackage(currentElement, helpp._1.asInstanceOf[ScPackage], helpp._2, conflictsReporter, project, editor)
        result += new ScopeItem("package " + helpp._1.getQualifiedName, helpp._2, Array[ScTypeElement](), Array[ScTypeElement](),
          null, Array(NameSuggester.suggestNamesByType(currentElement.calcType).apply(0).capitalize))
      }
    }

    result.toArray
  }

  private def getOccurrencesFromCompanionObject(typeElement: ScTypeElement,
                                                objectType: ScObject): Array[ScTypeElement] = {
    val parent: PsiElement = objectType.getParent
    val name = objectType.name
    val companion = parent.getChildren.find({
      case classType: ScClass if classType.name == name =>
        true
      case traitType: ScTrait if traitType.name == name =>
        true
      case _ => false
    })

    if (companion.isDefined)
      ScalaRefactoringUtil.getTypeElementOccurrences(typeElement, companion.get)
    else
      Array[ScTypeElement]()
  }

  //return Array of (package, containing directory)
  protected def getAllAvailablePackages(packageName: String, typeElement: ScTypeElement): Array[(PsiPackage, PsiDirectory)] = {
    def getDirectoriesContainigfile(file: PsiFile): Array[PsiDirectory] = {
      val result: ArrayBuffer[PsiDirectory] = new ArrayBuffer[PsiDirectory]()
      var parent = file.getContainingDirectory
      while (parent != null) {
        result += parent
        parent = parent.getParentDirectory
      }
      result.toArray
    }

    val dirContainingFile = getDirectoriesContainigfile(typeElement.getContainingFile)
    val module = RunWorksheetAction.getModuleFor(typeElement.getContainingFile)

    val result: ArrayBuffer[(PsiPackage, PsiDirectory)] = new ArrayBuffer[(PsiPackage, PsiDirectory)]()
    var currentPackage = ScPackageImpl.findPackage(typeElement.getProject, packageName).asInstanceOf[PsiPackage]
    while (currentPackage != null && currentPackage.getName != null) {
      val subPackages = currentPackage.getSubPackages(GlobalSearchScope.moduleScope(module))
      val filesNoRecursive = currentPackage.getFiles(GlobalSearchScope.moduleScope(module))

      // don't choose package if ther is only one subpackage
      if ((subPackages.length != 1) || filesNoRecursive.nonEmpty) {
        val packageDirectories = currentPackage.getDirectories(GlobalSearchScope.moduleScope(module))
        val containingDirectory = packageDirectories.intersect(dirContainingFile)

        val resultDirectory:PsiDirectory = if (containingDirectory.length > 0) {
          containingDirectory.apply(0)
        } else {
          typeElement.getContainingFile.getContainingDirectory
        }

        result += ((currentPackage, resultDirectory))
      }

      currentPackage = currentPackage.getParentPackage
    }

    result.toArray
  }

  def handleOnePackage(typeElement: ScTypeElement, inPackageName: String, containinDirectory: PsiDirectory,
                       conflictsReporter: ConflictsReporter, project: Project, editor: Editor, isReplaceAll: Boolean, inputName: String): ScopeItem = {
    def getFilesToSearchIn(currentDirectory: PsiDirectory): Array[PsiFile] = {
      if (!isReplaceAll) {
        Array(typeElement.getContainingFile)
      } else {
        def oneRound(word: String, bufResult: ArrayBuffer[ArrayBuffer[PsiFile]]) = {
          val buffer = new ArrayBuffer[PsiFile]()

          val processor = new Processor[PsiFile] {
            override def process(file: PsiFile): Boolean = {
              buffer += file
              true
            }
          }

          val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(typeElement.getProject)
          helper.processAllFilesWithWord(word, GlobalSearchScopesCore.directoryScope(currentDirectory, true), processor, true)

          bufResult += buffer
        }

        val typeName = typeElement.calcType.presentableText
        var words = Array[String]()
        words = StringUtil.getWordsIn(typeName).toArray(words)

        val resultBuffer = new ArrayBuffer[ArrayBuffer[PsiFile]]()
        words.foreach(oneRound(_, resultBuffer))

        var intersectionResult = resultBuffer(0)
        def intersect(inBuffer: ArrayBuffer[PsiFile]) = {
          intersectionResult = intersectionResult.intersect(inBuffer)
        }

        resultBuffer.foreach((element: ArrayBuffer[PsiFile]) => intersect(element))
        intersectionResult.toList.reverse.toArray
      }
    }

    val inPackage = ScPackageImpl.findPackage(typeElement.getProject, inPackageName)
    val projectSearchScope = GlobalSearchScope.projectScope(typeElement.getProject)
    val packageObject = inPackage.findPackageObject(projectSearchScope)

    val fileEncloser = if (packageObject.isDefined)
      PsiTreeUtil.getChildOfType(PsiTreeUtil.getChildOfType(packageObject.get, classOf[ScExtendsBlock]), classOf[ScTemplateBody])
    else
      containinDirectory

    val allOcurrences: mutable.MutableList[Array[ScTypeElement]] = mutable.MutableList()
    val allValidators: mutable.MutableList[ScalaTypeValidator] = mutable.MutableList()

    def handleOneFile(file: PsiFile) {
      if (packageObject.isDefined && packageObject.get.getContainingFile == file) {
      } else {
        val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(typeElement, file)
        allOcurrences += occurrences
        val parent = file match {
          case scalaFile: ScalaFile if scalaFile.isScriptFile() =>
            file
          case _ => PsiTreeUtil.findChildOfType(file, classOf[ScTemplateBody])
        }
        if (parent != null) {
          allValidators += ScalaTypeValidator(conflictsReporter, project, editor, file, typeElement, parent, occurrences.isEmpty)
        }
      }
    }

    val collectedFiles = getFilesToSearchIn(containinDirectory)

    var needNewDir = false
    // if we have no files in package, then we work with package in file
    if (inPackage.getDirectories.isEmpty) {
      needNewDir = true
      val classes = inPackage.getClasses
      for (clazz <- classes) {
        val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(typeElement, clazz)
        allOcurrences += occurrences

        val parent = PsiTreeUtil.findChildOfType(clazz, classOf[ScTemplateBody])
        val file = clazz.getContainingFile

        allValidators += ScalaTypeValidator(conflictsReporter, project, editor, file, typeElement, parent, occurrences.isEmpty)
      }
    } else {
      collectedFiles.foreach(handleOneFile)
    }

    val occurrences = allOcurrences.foldLeft(Array[ScTypeElement]())((a, b) => a ++ b)
    val validator = ScalaCompositeTypeValidator(allValidators.toList, conflictsReporter, project, typeElement,
      occurrences.isEmpty, containinDirectory, containinDirectory)

    val suggested = inputName
    val possibleNames = Array(validator.validateName(suggested, increaseNumber = true))

    val result = new ScopeItem("package " + inPackage.getName, fileEncloser, occurrences, Array[ScTypeElement](),
      validator, possibleNames.toArray)

    result.needDirectoryCreating = needNewDir

    result
  }
}

class ScopeItem(val name: String,
                val fileEncloser: PsiElement,
                val usualOccurrences: Array[ScTypeElement],
                val occurrencesInCompanion: Array[ScTypeElement],
                val typeValidator: ScalaValidator,
                val availableNames: Array[String]) {


  var occurrencesFromInheretors: Array[ScTypeElement] = Array[ScTypeElement]()
  var usualOccurrencesRanges = usualOccurrences.map((x: ScTypeElement) => (x.getTextRange, x.getContainingFile))
  var needDirectoryCreating = false

  def setInheretedOccurrences(occurrences: Array[ScTypeElement]) = {
    if (occurrences != null) {
      occurrencesFromInheretors = occurrences
    }
  }

  def isPackage: Boolean = {
    name.substring(0, 7) == "package"
  }

  def isTrait: Boolean = {
    name.substring(0, 5) == "trait"
  }

  def isClass: Boolean = {
    name.substring(0, 5) == "class"
  }

  def isObject: Boolean = {
    name.substring(0, 6) == "object"
  }

  def revalidate(newName: String): ScopeItem = {
    val revalidatedOccurrences = usualOccurrencesRanges.map((x: (TextRange, PsiFile)) =>
      PsiTreeUtil.findElementOfClassAtRange(x._2, x._1.getStartOffset, x._1.getEndOffset, classOf[ScTypeElement]))

    val newNames = if ((newName == "") || availableNames.contains(newName)) {
      availableNames
    } else {
      newName +: availableNames
    }

    new ScopeItem(name, fileEncloser,
      revalidatedOccurrences, occurrencesInCompanion, typeValidator, newNames)
  }

  override def toString: String = name
}
