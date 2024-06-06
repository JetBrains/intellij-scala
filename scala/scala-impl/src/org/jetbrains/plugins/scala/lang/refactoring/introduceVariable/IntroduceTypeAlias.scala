package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiTreeUtil.{findElementOfClassAtRange, getChildOfType, getParentOfType}
import org.jetbrains.annotations.{Nls, TestOnly}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, ValidSmartPointer, executeWriteActionCommand, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.ScTypePresentationExt
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.OccurrenceData.ReplaceOptions
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaDirectoryService, ScalaRefactoringUtil}
import org.jetbrains.plugins.scala.statistics.ScalaRefactoringUsagesCollector

import java.util

trait IntroduceTypeAlias {
  this: ScalaIntroduceVariableHandler =>

  val INTRODUCE_TYPEALIAS_REFACTORING_NAME: String = ScalaBundle.message("introduce.type.alias.title")

  def invokeTypeElement(file: PsiFile, inTypeElement: ScTypeElement)
                       (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    try {
      ScalaRefactoringUsagesCollector.logIntroduceTypeAlias(project)

      val documentManager = PsiDocumentManager.getInstance(project)
      documentManager.commitAllDocuments()
      writableScalaFile(file, INTRODUCE_TYPEALIAS_REFACTORING_NAME)

      if (isInvalid(inTypeElement)) {
        showErrorHintWithException(ScalaBundle.message("cannot.refactor.not.valid.type"), INTRODUCE_TYPEALIAS_REFACTORING_NAME)
      }

      val currentDataObject = editor.getUserData(IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO)

      if (currentDataObject.possibleScopes == null) {
        currentDataObject.setPossibleScopes(ScopeSuggester.suggestScopes(inTypeElement))
      }

      if (currentDataObject.possibleScopes.isEmpty) {
        showErrorHintWithException(ScalaBundle.message("cannot.refactor.scope.not.found"), INTRODUCE_TYPEALIAS_REFACTORING_NAME)
      }

      def runWithDialog(fromInplace: Boolean, mainScope: ScopeItem, enteredName: String = ""): Unit = {
        val possibleScopes = currentDataObject.possibleScopes

        val (updatedMainScope, updatedTypeElement) = mainScope match {
          case simpleScope: SimpleScopeItem if fromInplace =>
            val newScope = simpleScope.revalidate(enteredName)
            possibleScopes(possibleScopes.indexOf(mainScope)) = newScope

            val range = currentDataObject.initialTypeElement
            val newTypeElement = findElementOfClassAtRange(file, range.getStartOffset, range.getEndOffset, classOf[ScTypeElement]) match {
              case simpleType: ScSimpleTypeElement if isInvalid(simpleType) => getParentOfType(simpleType, classOf[ScParameterizedTypeElement])
              case typeElement => typeElement
            }

            (newScope, newTypeElement)
          case _: SimpleScopeItem | _: PackageScopeItem => (mainScope, inTypeElement)
          case _ => (possibleScopes(0), inTypeElement)
        }

        if (!ApplicationManager.getApplication.isUnitTestMode) {
          runWithDialogImpl(updatedTypeElement, possibleScopes, file, updatedMainScope)
        } else {
          runWithoutDialogImpl(updatedTypeElement, file, updatedMainScope)
        }
      }

      // replace all occurrences, don't replace occurences available from companion object or inheritors
      // suggest to choose scope
      def runInplace(): Unit = {
        def handleScope(scopeItem: SimpleScopeItem, needReplacement: Boolean): Unit = {
          val suggestedNames = scopeItem.availableNames

          val allOccurrences = OccurrenceData(inTypeElement,
            isReplaceAllUsual = true,
            isReplaceOccurrenceInCompanionObject = false,
            isReplaceOccurrenceInInheritors = false,
            scopeItem
          )

          executeWriteActionCommand(INTRODUCE_TYPEALIAS_REFACTORING_NAME) {
            val (namedElementReference, typeElementReference) = inWriteAction {
              runRefactoringForTypeInside(file, inTypeElement, suggestedNames.iterator().next(), allOccurrences, scopeItem)
            }

            val maybeTypeAlias = Option(namedElementReference.getElement).collect {
              case typeAlias: ScTypeAliasDefinition => typeAlias
            }

            Some(typeElementReference).collect {
              case ValidSmartPointer(e) => e.getTextOffset
            }.foreach { offset =>
              editor.getCaretModel.moveToOffset(offset)
              editor.getSelectionModel.removeSelection()

              if (isInplaceAvailable(editor)) {
                val document = editor.getDocument
                documentManager.commitDocument(document)
                documentManager.doPostponedOperationsAndUnblockDocument(document)

                maybeTypeAlias.foreach { typeAlias =>
                  editor.getUserData(IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO).addScopeElement(scopeItem)

                  new ScalaInplaceTypeAliasIntroducer(typeAlias)
                    .performInplaceRefactoring(new util.LinkedHashSet[String](suggestedNames))
                }
              }
            }
          }
        }

        val currentScope = currentDataObject.currentScope

        //need open modal dialog in inplace mode
        if ((StartMarkAction.canStart(editor) != null) && (currentScope != null)) {
          currentDataObject.isCallModalDialogInProgress = true
          val templateState: TemplateState = TemplateManagerImpl.getTemplateState(
            InjectedLanguageEditorUtil.getTopLevelEditor(editor)
          )

          if (templateState != null) {
            templateState.gotoEnd()
          }

          val enteredName = currentDataObject.getNamedElement.getName
          ScalaInplaceTypeAliasIntroducer.revertState(editor)

          runWithDialog(fromInplace = true, currentDataObject.currentScope, enteredName)
          //          editor.getUserData(IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO).clearData()
        } else {
          currentDataObject.setInintialInfo(inTypeElement.getTextRange)
          afterScopeChoosing(currentDataObject.possibleScopes, INTRODUCE_TYPEALIAS_REFACTORING_NAME) {
            case simpleScope: SimpleScopeItem if simpleScope.usualOccurrences.nonEmpty =>
              handleScope(simpleScope, needReplacement = true)
            case packageScope: PackageScopeItem =>
              runWithDialog(fromInplace = true, packageScope)
          }
        }
      }

      if (isInplaceAvailable(editor))
        runInplace()
      else
        runWithDialog(fromInplace = false, null)
    }

    catch {
      case _: IntroduceException =>
    }
  }

  private def runRefactoringForTypeInside(file: PsiFile,
                                          typeElement: ScTypeElement,
                                          typeName: String,
                                          occurrences: OccurrenceData,
                                          scope: ScopeItem)
                                         (implicit editor: Editor): (SmartPsiElementPointer[ScTypeAlias], SmartPsiElementPointer[ScTypeElement]) = {
    def addTypeAliasDefinition(typeName: String, typeElement: ScTypeElement, parent: PsiElement) = {
      def getAhchor(parent: PsiElement, firstOccurrence: PsiElement): Some[PsiElement] = {
        Some(parent.getChildren.find(_.getTextRange.contains(firstOccurrence.getTextRange)).getOrElse(parent.getLastChild))
      }


      val mtext = typeElement.calcType.canonicalCodeText(parent)

      val definition = ScalaPsiElementFactory
        .createTypeAliasDefinitionFromText(s"type $typeName = $mtext", typeElement.getContext, typeElement)

      val resultTypeAlias = ScalaPsiUtil.addTypeAliasBefore(definition, parent, getAhchor(parent, typeElement))
      ScalaPsiUtil.adjustTypes(resultTypeAlias, useTypeAliases = false)
      resultTypeAlias
    }

    val revertInfo = ScalaRefactoringUtil.RevertInfo(file.getText, editor.getCaretModel.getOffset)
    editor.putUserData(ScalaIntroduceVariableHandler.REVERT_INFO, revertInfo)

    val parent = scope match {
      case simpleScope: SimpleScopeItem =>
        simpleScope.fileEncloser
      case packageScope: PackageScopeItem =>
        packageScope.fileEncloser match {
          case suggestedDirectory: PsiDirectory =>
            createAndGetPackageObjectBody(suggestedDirectory, packageScope.needDirectoryCreating, scope.name)
          case _ =>
            packageScope.fileEncloser
        }
    }

    val typeAlias = addTypeAliasDefinition(typeName, occurrences.getAllOccurrences(0), parent)
    if (editor.getUserData(IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO) != null) {
      editor.getUserData(IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO).setTypeAlias(typeAlias)
    }

    val typeElementIdx = occurrences.getUsualOccurrences.indexWhere(_ == typeElement)

    val usualOccurrences = replaceTypeElements(occurrences.getUsualOccurrences, typeName, typeAlias)
    replaceTypeElements(occurrences.getExtendedOccurrences, typeName, typeAlias)

    val className = parent.parentOfType(classOf[ScObject])
      .map(_.name).getOrElse("")

    replaceTypeElements(occurrences.getCompanionObjOccurrences, className + "." + typeName, typeAlias)

    val resultTypeElement = if (typeElementIdx == -1) {
      replaceTypeElements(Array(typeElement), typeName, typeAlias).apply(0)
    } else {
      usualOccurrences.apply(typeElementIdx)
    }

    (typeAlias.createSmartPointer, resultTypeElement.createSmartPointer)
  }

  def runRefactoringForTypes(file: PsiFile, typeElement: ScTypeElement, typeName: String,
                             occurrences_ : OccurrenceData, scope: ScopeItem)
                            (implicit project: Project, editor: Editor): Unit = {
    executeWriteActionCommand(INTRODUCE_TYPEALIAS_REFACTORING_NAME) {
      runRefactoringForTypeInside(file, typeElement, typeName, occurrences_, scope)
    }
    editor.getSelectionModel.removeSelection()
  }

  private def afterScopeChoosing(scopes: Array[ScopeItem], refactoringName: String)
                                (invokesNext: ScopeItem => Unit)
                                (implicit project: Project, editor: Editor): Unit = {

    def chooseScopeItem(item: ScopeItem): Unit = {
      invokesNext(item)
    }

    showTypeAliasChooser(scopes, (elem: ScopeItem) => chooseScopeItem(elem),
      ScalaBundle.message("choose.scope.for", refactoringName), (elem: ScopeItem) => elem.toString)
  }

  private def replaceTypeElements(occurrences: Array[ScTypeElement], name: String, typeAlias: ScTypeAlias): Array[ScTypeElement] = {
    def replaceHelper(typeElement: ScTypeElement, inName: String): ScTypeElement = {
      val replacement = ScalaPsiElementFactory.createTypeElementFromText(inName, typeElement.getContext, typeElement)
      //remove parethesis around typeElement
      if (typeElement.getParent.is[ScParenthesisedTypeElement]) {
        typeElement.getNextSibling.delete()
        typeElement.getPrevSibling.delete()
      }

      //avoid replacing typeelement that was replaced
      if (typeElement.calcType.codeText(typeElement) == inName) {
        typeElement
      } else {
        typeElement.replace(replacement).asInstanceOf[ScTypeElement]
      }
    }

    def bindHelper(typeElement: ScTypeElement) = {
      typeElement.getFirstChild.asInstanceOf[ScStableCodeReference].bindToElement(typeAlias)
      typeElement
    }

    val replaced = occurrences.map(replaceHelper(_, name))
    replaced.map(bindHelper)
    //    occurrences
  }


  private def showTypeAliasChooser[T](elements: Array[T], pass: T => Unit, @Nls title: String, elementName: T => String)
                                     (implicit project: Project, editor: Editor): Unit =
    showChooserGeneric(
      elements.toSeq,
      pass,
      title,
      elementName,
      (_: T) => null // do not highlight on item selection
    )

  private def createAndGetPackageObjectBody(suggestedDirectory: PsiDirectory,
                                            needCreateDirectory: Boolean,
                                            inNewDirectoryName: String): ScTemplateBody = {
    val newDirectoryName = if (needCreateDirectory) {
      inNewDirectoryName
    } else {
      "package"
    }

    val currentDirectory = suggestedDirectory
    val newDir = if (needCreateDirectory) {
      currentDirectory.createSubdirectory(newDirectoryName)
    }
    else {
      currentDirectory
    }

    val packageObject: ScTypeDefinition =
      ScalaDirectoryService.createClassFromTemplate(newDir, newDirectoryName, "Package Object", askToDefineVariables = false)
        .asInstanceOf[ScTypeDefinition]

    getChildOfType(getChildOfType(packageObject, classOf[ScExtendsBlock]), classOf[ScTemplateBody])
  }

  private def runWithDialogImpl(
    typeElement: ScTypeElement,
    possibleScopes: Array[ScopeItem],
    file: PsiFile,
    mainScope: ScopeItem
  )(implicit project: Project, editor: Editor): Unit = {
    val occurrencesRanges = mainScope match {
      case simpleScope: SimpleScopeItem => simpleScope.usualOccurrences.toSeq.map(_.getTextRange)
      case _: PackageScopeItem => Seq.empty[TextRange]
    }

    val dialog = new ScalaIntroduceTypeAliasDialog(project, typeElement, possibleScopes, mainScope, this)

    this.runWithDialogImpl(dialog, occurrencesRanges) { dialog =>
      val occurrences = OccurrenceData(
        typeElement,
        dialog.isReplaceAllOccurrences,
        dialog.isReplaceOccurrenceIncompanionObject,
        dialog.isReplaceOccurrenceInInheritors,
        dialog.getSelectedScope
      )

      runRefactoringForTypes(file,
        typeElement,
        dialog.getEnteredName,
        occurrences,
        dialog.getSelectedScope
      )
  }
  }

  @TestOnly
  private def runWithoutDialogImpl(
    typeElement: ScTypeElement,
    file: PsiFile,
    mainScope: ScopeItem
  )(implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    val scopeItem = mainScope

    val testReplaceOptions = Option(dataContext.getData(ScalaIntroduceVariableHandler.ForcedReplaceTestOptions))
    val typeName = testReplaceOptions.flatMap(_.definitionName).getOrElse(scopeItem.name)
    val replaceOptions: ReplaceOptions = testReplaceOptions.map(_.toProductionReplaceOptions).getOrElse(ReplaceOptions.DefaultInTests)

    val occurrences = OccurrenceData(
      typeElement = typeElement,
      replaceOptions,
      scopeItem = scopeItem,
    )
    runRefactoringForTypes(
      file,
      typeElement,
      typeName = typeName,
      occurrences,
      scopeItem
    )
  }
}

object IntroduceTypeAlias {
  val REVERT_TYPE_ALIAS_INFO: Key[IntroduceTypeAliasData] = new Key("RevertTypeAliasInfo")
}
