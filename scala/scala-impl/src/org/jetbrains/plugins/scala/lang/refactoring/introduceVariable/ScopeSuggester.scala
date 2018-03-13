package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.{util => ju}

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, GlobalSearchScopesCore, PsiSearchHelper}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util._
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction

import scala.annotation.tailrec
import scala.collection.JavaConverters._
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

    def isSuitableParent(owners: Seq[ScTypeParametersOwner], parent: PsiElement): Boolean = {
      var result = true
      for (elementOwner <- owners) {
        val pparent = PsiTreeUtil.getParentOfType(parent, classOf[ScTemplateDefinition])
        if (pparent != null && (!elementOwner.isAncestorOf(pparent) || !elementOwner.isInstanceOf[ScTemplateDefinition])) {
          result = false
        }
      }
      result
    }

    val isScriptFile = currentElement.getContainingFile.asInstanceOf[ScalaFile].isScriptFile

    val owners = ScalaRefactoringUtil.getTypeParameterOwnerList(currentElement) ++ ScalaRefactoringUtil.getTypeAliasOwnersList(currentElement)
    var parent = getParent(currentElement, isScriptFile)

    //forbid to work with no template definition level
    var noContinue = owners.exists(!_.isInstanceOf[ScTemplateDefinition])
    val result: ArrayBuffer[ScopeItem] = new ArrayBuffer[ScopeItem]()
    while (parent != null && !noContinue) {
      var occInCompanionObj: Array[ScTypeElement] = Array[ScTypeElement]()
      val containerName = parent match {
        case fileType: ScalaFile => Some("file " + fileType.getName)
        case _ =>
          PsiTreeUtil.getParentOfType(parent, classOf[ScTemplateDefinition]) match {
            case classType: ScClass =>
              Some("class " + classType.name)
            case objectType: ScObject =>
              occInCompanionObj = getOccurrencesFromCompanionObject(currentElement, objectType)
              Some("object " + objectType.name)
            case traitType: ScTrait =>
              Some("trait " + traitType.name)
            case _ => None
          }
      }

      //parent != null here
      //check can we use upper scope
      noContinue = currentElement.calcType match {
        case projectionType: ScProjectionType =>
          //we can't use typeAlias outside scope where it was defined
          parent.asInstanceOf[ScTemplateBody].isAncestorOf(projectionType.actualElement)
        case _ => false
      }

      if (!isSuitableParent(owners, parent)) {
        noContinue = true
      }

      val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(currentElement, parent)
      val validator = ScalaTypeValidator(currentElement, parent, occurrences.isEmpty)

      val possibleNames = NameSuggester.suggestNamesByType(currentElement.calcType)(validator)

      containerName.foreach { name =>
        result += SimpleScopeItem(name, parent, occurrences, occInCompanionObj, validator, possibleNames.toSet.asJava)
      }
      parent = getParent(parent, isScriptFile)
    }

    val scPackage = PsiTreeUtil.getParentOfType(currentElement, classOf[ScPackaging])

    //forbid to use typeParameter type outside the class
    if ((scPackage != null) && owners.isEmpty && !noContinue) {
      val allPackages = getAllAvailablePackages(scPackage.fullPackageName, currentElement)
      for ((resultPackage, resultDirectory) <- allPackages) {
        val suggested = NameSuggester.suggestNamesByType(currentElement.calcType).map(_.capitalize).head
        result += PackageScopeItem(resultPackage.getQualifiedName, resultDirectory, needDirectoryCreating = false, Set(suggested).asJava)
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

    @tailrec
    def getDirectoriesContainigFileAndPackage(currentPackage: PsiPackage,
                                              module: Module,
                                              result: ArrayBuffer[(PsiPackage, PsiDirectory)],
                                              dirContainingFile: Array[PsiDirectory]): ArrayBuffer[(PsiPackage, PsiDirectory)] = {

      if (currentPackage != null && currentPackage.getName != null) {
        val subPackages = currentPackage.getSubPackages(GlobalSearchScope.moduleScope(module))
        val filesNoRecursive = currentPackage.getFiles(GlobalSearchScope.moduleScope(module))

        // don't choose package if ther is only one subpackage
        if ((subPackages.length != 1) || filesNoRecursive.nonEmpty) {
          val packageDirectories = currentPackage.getDirectories(GlobalSearchScope.moduleScope(module))
          val containingDirectory = packageDirectories.intersect(dirContainingFile)

          val resultDirectory: PsiDirectory = if (containingDirectory.length > 0) {
            containingDirectory.apply(0)
          } else {
            typeElement.getContainingFile.getContainingDirectory
          }

          result += ((currentPackage, resultDirectory))
        }
        getDirectoriesContainigFileAndPackage(currentPackage.getParentPackage, module, result, dirContainingFile)
      } else {
        result
      }
    }

    val currentPackage = ScPackageImpl.findPackage(typeElement.getProject, packageName).asInstanceOf[PsiPackage]
    val directoriesContainingFile = getDirectoriesContainigfile(typeElement.getContainingFile)
    val module = RunWorksheetAction.getModuleFor(typeElement.getContainingFile)
    val result: ArrayBuffer[(PsiPackage, PsiDirectory)] = new ArrayBuffer[(PsiPackage, PsiDirectory)]()

    getDirectoriesContainigFileAndPackage(currentPackage, module, result, directoriesContainingFile)

    result.toArray
  }

  def handleOnePackage(typeElement: ScTypeElement, inPackageName: String, containinDirectory: PsiDirectory,
                       conflictsReporter: ConflictsReporter, project: Project, editor: Editor, isReplaceAll: Boolean, inputName: String): PackageScopeItem = {
    def getFilesToSearchIn(currentDirectory: PsiDirectory): Array[ScalaFile] = {
      if (!isReplaceAll) {
        Array(typeElement.getContainingFile.asInstanceOf[ScalaFile])
      } else {
        def oneRound(word: String, bufResult: ArrayBuffer[ArrayBuffer[ScalaFile]]) = {
          val buffer = new ArrayBuffer[ScalaFile]()

          val processor = new Processor[PsiFile] {
            override def process(file: PsiFile): Boolean = {
              file match {
                case scalaFile: ScalaFile =>
                  buffer += scalaFile
              }
              true
            }
          }

          val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(typeElement.getProject)
          helper.processAllFilesWithWord(word, GlobalSearchScopesCore.directoryScope(currentDirectory, true), processor, true)

          bufResult += buffer
        }

        val typeName = typeElement.calcType.codeText
        val words = StringUtil.getWordsIn(typeName).asScala.toArray

        val resultBuffer = new ArrayBuffer[ArrayBuffer[ScalaFile]]()
        words.foreach(oneRound(_, resultBuffer))

        var intersectionResult = resultBuffer(0)
        def intersect(inBuffer: ArrayBuffer[ScalaFile]): Unit = {
          intersectionResult = intersectionResult.intersect(inBuffer)
        }

        resultBuffer.foreach((element: ArrayBuffer[ScalaFile]) => intersect(element))
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

    def handleOneFile(file: ScalaFile) {
      if (packageObject.exists((x: ScTypeDefinition) => x.getContainingFile == file)) {
      } else {
        val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(typeElement, file)
        allOcurrences += occurrences
        val parent = file match {
          case scalaFile: ScalaFile if scalaFile.isScriptFile =>
            file
          case _ => PsiTreeUtil.findChildOfType(file, classOf[ScTemplateBody])
        }
        if (parent != null) {
          allValidators += ScalaTypeValidator(typeElement, parent, occurrences.isEmpty)
        }
      }
    }

    val collectedFiles = getFilesToSearchIn(containinDirectory)

    val needNewDir = inPackage.getDirectories.isEmpty
    // if we have no files in package, then we work with package in file
    if (needNewDir) {
      val classes = inPackage.getClasses
      for (clazz <- classes) {
        val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(typeElement, clazz)
        allOcurrences += occurrences

        val parent = PsiTreeUtil.findChildOfType(clazz, classOf[ScTemplateBody])

        allValidators += ScalaTypeValidator(typeElement, parent, occurrences.isEmpty)
      }
    } else {
      collectedFiles.foreach(handleOneFile)
    }

    val occurrences = allOcurrences.foldLeft(Array[ScTypeElement]())((a, b) => a ++ b)
    val validator = ScalaCompositeTypeValidator(allValidators.toList, conflictsReporter, project, typeElement,
      occurrences.isEmpty, containinDirectory, containinDirectory)

    val possibleName = validator.validateName(inputName)

    val result = PackageScopeItem(inPackage.getName, fileEncloser, needNewDir, Set(possibleName).asJava)

    result.occurrences = occurrences
    result.validator = validator

    result
  }
}


abstract class ScopeItem(val name: String, val availableNames: ju.Set[String]) {
  override def toString: String = name
}

case class SimpleScopeItem(override val name: String,
                           fileEncloser: PsiElement,
                           usualOccurrences: Array[ScTypeElement],
                           occurrencesInCompanion: Array[ScTypeElement],
                           typeValidator: ScalaTypeValidator,
                           override val availableNames: ju.Set[String]) extends ScopeItem(name, availableNames) {

  var occurrencesFromInheritors: Array[ScTypeElement] = Array[ScTypeElement]()
  private val usualOccurrencesRanges: Array[(TextRange, PsiFile)] = usualOccurrences.map((x: ScTypeElement) => (x.getTextRange, x.getContainingFile))
  private val fileEncloserRange: (TextRange, PsiFile) = (fileEncloser.getTextRange, fileEncloser.getContainingFile)

  def setInheritedOccurrences(occurrences: Array[ScTypeElement]): Unit = {
    if (occurrences != null) {
      occurrencesFromInheritors = occurrences
    }
  }

  def revalidate(newName: String): ScopeItem = {
    val revalidatedOccurrences = usualOccurrencesRanges.map {
      case (range, containingFile) =>
        PsiTreeUtil.findElementOfClassAtRange(containingFile, range.getStartOffset, range.getEndOffset, classOf[ScTypeElement])
    }

    val newNames: Set[String] = availableNames.asScala.toSet ++ (newName match {
      case "" => Set.empty
      case n if availableNames.contains(n) => Set.empty
      case n => Set(n)
    })

    val updatedFileEncloser = fileEncloserRange match {
      case (range, containingFile) =>
        PsiTreeUtil.findElementOfClassAtRange(containingFile, range.getStartOffset, range.getEndOffset, classOf[PsiElement])
    }

    val updatedValidator = new ScalaTypeValidator(typeValidator.selectedElement, typeValidator.noOccurrences, updatedFileEncloser, updatedFileEncloser)

    SimpleScopeItem(name, updatedFileEncloser,
      revalidatedOccurrences, occurrencesInCompanion, updatedValidator, newNames.asJava)
  }

  def isTrait: Boolean = {
    name.startsWith("trait")
  }

  def isClass: Boolean = {
    name.startsWith("class")
  }

  def isObject: Boolean = {
    name.startsWith("object")
  }
}

case class PackageScopeItem(override val name: String,
                            fileEncloser: PsiElement,
                            needDirectoryCreating: Boolean,
                            override val availableNames: ju.Set[String]) extends ScopeItem(name, availableNames) {
  var occurrences: Array[ScTypeElement] = Array[ScTypeElement]()
  var validator: ScalaCompositeTypeValidator = _

  override def toString: String = "package " + name
}