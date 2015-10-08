package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.awt.Component
import java.util
import javax.swing.event.{ListSelectionEvent, ListSelectionListener}

import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.{HighlighterLayer, HighlighterTargetArea, RangeHighlighter, TextAttributes}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{JBPopupAdapter, JBPopupFactory, LightweightWindowEvent}
import com.intellij.openapi.util.Computable
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.lang.refactoring.util.{DefaultListCellRendererAdapter, ScalaDirectoryService, ScalaRefactoringUtil}
import org.jetbrains.plugins.scala.util.{JListCompatibility, ScalaUtils}

/**
 * Created by Kate Ustyuzhanina
 * on 9/18/15
 */
trait IntroduceTypeAlias {
  this: ScalaIntroduceVariableHandler =>

  val INTRODUCE_TYPEALIAS_REFACTORING_NAME = ScalaBundle.message("introduce.type.alias.title")

  def invokeTypeElement(project: Project, editor: Editor, file: PsiFile, inTypeElement: ScTypeElement): Unit = {
    try {
      UsageTrigger.trigger(ScalaBundle.message("introduce.variable.id"))

      PsiDocumentManager.getInstance(project).commitAllDocuments()
      ScalaRefactoringUtil.checkFile(file, project, editor, INTRODUCE_TYPEALIAS_REFACTORING_NAME)

      val typeElement: ScTypeElement = ScalaRefactoringUtil.checkTypeElement(inTypeElement).
        getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.valid.type"), project, editor, INTRODUCE_TYPEALIAS_REFACTORING_NAME))


      if (IntroduceTypeAliasData.possibleScopes == null) {
        IntroduceTypeAliasData.setPossibleScopes(ScopeSuggester.suggestScopes(this, project, editor, file, typeElement))
      }

      if (IntroduceTypeAliasData.possibleScopes.isEmpty) {
        showErrorMessage(ScalaBundle.message("cannot.refactor.scope.not.found"), project, editor, INTRODUCE_TYPEALIAS_REFACTORING_NAME)
      }

      def runWithDialog(fromInplace: Boolean, mainScope: ScopeItem, enteredName: String = "") {
        val typeElementHelper = if (fromInplace) {
          val range = IntroduceTypeAliasData.initialTypeElement
          PsiTreeUtil.findElementOfClassAtRange(file, range.getStartOffset, range.getEndOffset, classOf[ScTypeElement])
          match {
            case simpleType: ScSimpleTypeElement =>
              if (simpleType.getNextSiblingNotWhitespace.isInstanceOf[ScTypeArgs]) {
                PsiTreeUtil.getParentOfType(simpleType, classOf[ScParameterizedTypeElement])
              } else {
                simpleType
              }
            case typeElement: ScTypeElement =>
              typeElement
          }
        } else {
          typeElement
        }

        val updatedMainScope = if (fromInplace) {
          val newScope = mainScope.revalidate(enteredName)
          val mainScopeIdx = IntroduceTypeAliasData.possibleScopes.indexOf(mainScope)
          IntroduceTypeAliasData.possibleScopes(mainScopeIdx) = newScope
          newScope
        } else {
          mainScope
        }

        val dialog = getDialogForTypes(project, editor, typeElementHelper, IntroduceTypeAliasData.possibleScopes, updatedMainScope)
        if (!dialog.isOK) {
          occurrenceHighlighters.foreach(_.dispose())
          occurrenceHighlighters = Seq.empty
          return
        }

        val occurrences: OccurrenceData = OccurrenceData(typeElementHelper,
          dialog.isReplaceAllOccurrences,
          dialog.isReplaceOccurrenceIncompanionObject,
          dialog.isReplaceOccurrenceInInheritors, dialog.getSelectedScope)

        val parent = dialog.getSelectedScope.fileEncloser
        runRefactoringForTypes(file, editor, typeElementHelper, dialog.getEnteredName, occurrences, parent,
          dialog.getSelectedScope.isPackage, dialog.getSelectedScope.needDirectoryCreating)
      }

      // replace all occurrences, don't replace occurences available from companion object or inheritors
      // suggest to choose scope
      def runInplace() = {
        def handleScope(scopeItem: ScopeItem, needReplacement: Boolean) {
          val replaceAllOccurrences = true
          val suggestedNames = scopeItem.availableNames

          import scala.collection.JavaConversions.asJavaCollection
          val suggestedNamesSet = new util.LinkedHashSet[String](suggestedNames.toIterable)
          val allOccurrences = OccurrenceData(typeElement, replaceAllOccurrences, isReplaceOccurrenceIncompanionObject = false,
            isReplaceOccurrenceInInheritors = false, scopeItem)

          val introduceRunnable: Computable[(SmartPsiElementPointer[PsiElement], SmartPsiElementPointer[PsiElement])] =
            introduceTypeAlias(file, editor, typeElement, allOccurrences, suggestedNames(0), replaceAllOccurrences,
              scopeItem.fileEncloser, scopeItem.isPackage, scopeItem.needDirectoryCreating)

          CommandProcessor.getInstance.executeCommand(project, new Runnable {
            def run() {
              val computable = ApplicationManager.getApplication.runWriteAction(introduceRunnable)

              val namedElement: ScNamedElement =
                computable._1.getElement match {
                  case typeAlias: ScTypeAliasDefinition =>
                    typeAlias
                  case _ => null
                }

              val mtypeElement = computable._2.getElement match {
                case typeElement: ScTypeElement =>
                  typeElement
                case _ => null
              }

              if (mtypeElement != null && mtypeElement.isValid) {
                editor.getCaretModel.moveToOffset(mtypeElement.getTextOffset)
                editor.getSelectionModel.removeSelection()
                if (ScalaRefactoringUtil.isInplaceAvailable(editor)) {

                  PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
                  PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument)

                  val typeAliasIntroducer =
                    ScalaInplaceTypeAliasIntroducer(namedElement, namedElement, editor, namedElement.getName,
                      namedElement.getName, scopeItem)

                  typeAliasIntroducer.performInplaceRefactoring(suggestedNamesSet)
                }
              }
            }
          }, INTRODUCE_TYPEALIAS_REFACTORING_NAME, null)
        }

        val currentScope = IntroduceTypeAliasData.currentScope

        //need open modal dialog in inplace mode
        if ((StartMarkAction.canStart(project) != null) && (currentScope != null)) {
          IntroduceTypeAliasData.isCallModalDialogInProgress = true
          val templateState: TemplateState = TemplateManagerImpl.getTemplateState(InjectedLanguageUtil.getTopLevelEditor(editor))

          if (templateState != null) {
            templateState.cancelTemplate()
          }

          val enteredName = IntroduceTypeAliasData.getNamedElement.getName
          ScalaInplaceTypeAliasIntroducer.revertState(editor, IntroduceTypeAliasData.currentScope, IntroduceTypeAliasData.getNamedElement)

          runWithDialog(fromInplace = true, IntroduceTypeAliasData.currentScope, enteredName)
          IntroduceTypeAliasData.clearData()
        } else {
          IntroduceTypeAliasData.setInintialInfo(inTypeElement.getTextRange)
          afterScopeChoosing(project, editor, file, IntroduceTypeAliasData.possibleScopes, INTRODUCE_TYPEALIAS_REFACTORING_NAME) {
            scopeItem =>
              if (!scopeItem.usualOccurrences.isEmpty) {
                //handle packageObject in modal dialog because we can't change name & call refactoring
                if (scopeItem.isPackage) {
                  runWithDialog(fromInplace = true, scopeItem)
                } else {
                  handleScope(scopeItem, needReplacement = true)
                }
              }
          }
        }
      }

      if (ScalaRefactoringUtil.isInplaceAvailable(editor)) runInplace()
      else runWithDialog(fromInplace = false, null)
    }

    catch {
      case _: IntroduceException =>
    }
  }

  private def runRefactoringForTypeInside(file: PsiFile,
                                          editor: Editor,
                                          typeElement: ScTypeElement,
                                          typeName: String,
                                          occurrences: OccurrenceData,
                                          suggestedParent: PsiElement,
                                          isPackage: Boolean,
                                          needCreateDirectory: Boolean): (SmartPsiElementPointer[PsiElement], SmartPsiElementPointer[PsiElement]) = {
    def addTypeAliasDefinition(typeName: String, typeElement: ScTypeElement, parent: PsiElement) = {
      def getAhchor(parent: PsiElement, firstOccurrence: PsiElement): Some[PsiElement] = {
        Some(parent.getChildren.find(_.getTextRange.contains(firstOccurrence.getTextRange)).getOrElse(parent.getLastChild))
      }

      val mtext = typeElement.calcType.canonicalText

      val definition = ScalaPsiElementFactory
        .createTypeAliasDefinitionFromText(s"type $typeName = $mtext", typeElement.getContext, typeElement)

      val resultTypeAlias = ScalaPsiUtil.addTypeAliasBefore(definition, parent, getAhchor(parent, typeElement))
      ScalaPsiUtil.adjustTypes(resultTypeAlias, addImports = true, useTypeAliases = false)
      resultTypeAlias
    }

    val revertInfo = ScalaRefactoringUtil.RevertInfo(file.getText, editor.getCaretModel.getOffset)
    editor.putUserData(ScalaIntroduceVariableHandler.REVERT_INFO, revertInfo)

    val parent = suggestedParent match {
      case suggestedDirectory: PsiDirectory if isPackage =>
        createAndGetPackageObjectBody(typeElement, suggestedDirectory, needCreateDirectory)
      case _ =>
        suggestedParent
    }


    val typeAlias = addTypeAliasDefinition(typeName, occurrences.getAllOccurrences(0), parent)
    IntroduceTypeAliasData.setTypeAlias(typeAlias)

    val typeElementIdx = occurrences.getUsualOccurrences.indexWhere(_ == typeElement)

    val usualOccurrences = replaceTypeElements(occurrences.getUsualOccurrences, typeName, typeAlias)
    replaceTypeElements(occurrences.getExtendedOccurrences, typeName, typeAlias)

    val className = PsiTreeUtil.getParentOfType(parent, classOf[ScObject]) match {
      case objectType: ScObject =>
        objectType.name
      case _ => ""
    }

    replaceTypeElements(occurrences.getCompanionObjOccurrences, className + "." + typeName, typeAlias)

    (SmartPointerManager.getInstance(file.getProject).createSmartPsiElementPointer(typeAlias.asInstanceOf[PsiElement]),
      SmartPointerManager.getInstance(file.getProject).createSmartPsiElementPointer(usualOccurrences.apply(typeElementIdx)))
  }

  def runRefactoringForTypes(file: PsiFile, editor: Editor,
                             typeElement: ScTypeElement, typeName: String,
                             occurrences_ : OccurrenceData, parent: PsiElement,
                             isPackage: Boolean, needCreateDirectory: Boolean) = {
    val runnable = new Runnable() {
      def run() {
        runRefactoringForTypeInside(file, editor, typeElement, typeName, occurrences_, parent, isPackage, needCreateDirectory)
      }
    }
    ScalaUtils.runWriteAction(runnable, editor.getProject, INTRODUCE_TYPEALIAS_REFACTORING_NAME)
    editor.getSelectionModel.removeSelection()
  }

  protected def introduceTypeAlias(file: PsiFile,
                                   editor: Editor,
                                   typeElement: ScTypeElement,
                                   occurrences_ : OccurrenceData,
                                   typeName: String,
                                   replaceAllOccurrences: Boolean,
                                   parent: PsiElement,
                                   isPackage: Boolean,
                                   needCreateDirectory: Boolean): Computable[(SmartPsiElementPointer[PsiElement], SmartPsiElementPointer[PsiElement])] = {

    new Computable[(SmartPsiElementPointer[PsiElement], SmartPsiElementPointer[PsiElement])]() {
      def compute() = runRefactoringForTypeInside(file, editor, typeElement, typeName, occurrences_, parent, isPackage, needCreateDirectory)
    }
  }

  def afterScopeChoosing(project: Project, editor: Editor, file: PsiFile, scopes: Array[ScopeItem],
                         refactoringName: String)(invokesNext: (ScopeItem) => Unit) {

    def chooseScopeItem(item: ScopeItem): Unit = {
      invokesNext(item)
    }
    showTypeAliasChooser(editor, scopes, (elem: ScopeItem) => chooseScopeItem(elem),
      ScalaBundle.message("choose.scope.for", refactoringName), (elem: ScopeItem) => elem.toString)
  }

  def replaceTypeElements(occurrences: Array[ScTypeElement], name: String, typeAlias: ScTypeAlias) = {
    def replaceHelper(typeElement: ScTypeElement, inName: String): ScTypeElement = {
      val replacement = ScalaPsiElementFactory.createTypeElementFromText(inName, typeElement.getContext, typeElement)
      //remove parethesis around typeElement
      if (typeElement.getParent.isInstanceOf[ScParenthesisedTypeElement]) {
        typeElement.getNextSibling.delete()
        typeElement.getPrevSibling.delete()
      }

      //avoid replacing typeelement that was replaced
      if (typeElement.calcType.presentableText == inName) {
        typeElement
      } else {
        typeElement.replace(replacement).asInstanceOf[ScTypeElement]
      }
    }

    def bindHelper(typeElement: ScTypeElement) = {
      typeElement.getFirstChild.asInstanceOf[ScStableCodeReferenceElement].bindToElement(typeAlias)
      typeElement
    }

    val replaced = occurrences.map(replaceHelper(_, name))
    replaced.map(bindHelper)
    //    occurrences
  }


  def showTypeAliasChooser[T](editor: Editor, elements: Array[T], pass: T => Unit, title: String, elementName: T => String) {
    class Selection {
      val selectionModel = editor.getSelectionModel
      val (start, end) = (selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
      val scheme = editor.getColorsScheme
      val textAttributes = new TextAttributes
      textAttributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR))
      textAttributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
      var selectionHighlighter: RangeHighlighter = null
      val markupModel = editor.getMarkupModel

      def addHighlighter() = if (selectionHighlighter == null) {
        selectionHighlighter = markupModel.addRangeHighlighter(start, end, HighlighterLayer.SELECTION + 1,
          textAttributes, HighlighterTargetArea.EXACT_RANGE)
      }

      def removeHighlighter() = if (selectionHighlighter != null) markupModel.removeHighlighter(selectionHighlighter)
    }

    val selection = new Selection
    val highlighter: ScopeHighlighter = new ScopeHighlighter(editor)
    val model = JListCompatibility.createDefaultListModel()
    for (element <- elements) {
      JListCompatibility.addElement(model, element)
    }
    val list = JListCompatibility.createJListFromModel(model)
    JListCompatibility.setCellRenderer(list, new DefaultListCellRendererAdapter {
      def getListCellRendererComponentAdapter(container: JListCompatibility.JListContainer,
                                              value: Object, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
        val rendererComponent: Component = getSuperListCellRendererComponent(container.getList, value, index, isSelected, cellHasFocus)
        val element: T = value.asInstanceOf[T]
        //        if (element.isValid) {
        setText(elementName(element))
        //        }
        rendererComponent
      }
    })
    list.addListSelectionListener(new ListSelectionListener {
      def valueChanged(e: ListSelectionEvent) {
        highlighter.dropHighlight()
        val index: Int = list.getSelectedIndex
        if (index < 0) return
      }
    })

    JBPopupFactory.getInstance.createListPopupBuilder(list).setTitle(title).setMovable(false).setResizable(false).setRequestFocus(true).setItemChoosenCallback(new Runnable {
      def run() {
        pass(list.getSelectedValue.asInstanceOf[T])
      }
    }).addListener(new JBPopupAdapter {
      override def beforeShown(event: LightweightWindowEvent): Unit = {
        selection.addHighlighter()
      }

      override def onClosed(event: LightweightWindowEvent) {
        highlighter.dropHighlight()
        selection.removeHighlighter()
      }
    }).createPopup.showInBestPositionFor(editor)
  }

  protected def createAndGetPackageObjectBody(typeElement: ScTypeElement,
                                              suggestedDirectory: PsiDirectory,
                                              needCreateDirectory: Boolean): ScTemplateBody = {
    val newDirectoryName = "One"
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

    PsiTreeUtil.getChildOfType(PsiTreeUtil.getChildOfType(packageObject, classOf[ScExtendsBlock]), classOf[ScTemplateBody])
  }

  protected def getDialogForTypes(project: Project, editor: Editor, typeElement: ScTypeElement,
                                  possibleScopes: Array[ScopeItem], mainScope: ScopeItem): ScalaIntroduceTypeAliasDialog = {

    // Add occurrences highlighting
    val occurrences = possibleScopes.apply(0).usualOccurrences
    if (occurrences.length > 1)
      occurrenceHighlighters = ScalaRefactoringUtil.highlightOccurrences(project, occurrences.map(_.getTextRange), editor)

    val dialog = new ScalaIntroduceTypeAliasDialog(project, typeElement, possibleScopes, mainScope, this, editor)
    dialog.show()
    if (!dialog.isOK) {
      if (occurrences.length > 1) {
        WindowManager.getInstance.getStatusBar(project).
          setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
      }
    }

    dialog
  }
}
