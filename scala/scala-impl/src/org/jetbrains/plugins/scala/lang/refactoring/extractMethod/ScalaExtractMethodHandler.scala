package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import com.intellij.openapi.actionSystem.{DataContext, DataKey}
import com.intellij.openapi.application.{ApplicationManager, ModalityState, ReadAction}
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefinitionsCollector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createNewLine, createTemplateDefinitionFromText}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, TypeAdjuster}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaRefactoringActionHandler
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodHandler.ChosenTargetScopeKey
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates.{DuplicateMatch, DuplicatesUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.statistics.ScalaRefactoringUsagesCollector
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

class ScalaExtractMethodHandler extends ScalaRefactoringActionHandler {
  private val REFACTORING_NAME: String = ScalaBundle.message("extract.method.title")

  override def invoke(file: PsiFile)
                     (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

    ScalaRefactoringUsagesCollector.logExtractMethod(project)

    file.findScalaLikeFile.foreach { scalaFile =>
      afterExpressionChoosing(scalaFile, REFACTORING_NAME) {
        invokeOnEditor(scalaFile)
      }
    }
  }

  private def invokeOnEditor(file: PsiFile)
                            (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    implicit val ctx: ProjectContext = project

    val scalaFile = maybeWritableScalaFile(file, REFACTORING_NAME)
      .getOrElse(return)

    if (!editor.getSelectionModel.hasSelection) return
    val elements = selectedElements(editor, scalaFile, trimComments = false)

    if (showNotPossibleWarnings(elements, REFACTORING_NAME)) return

    def checkLastReturn(elem: PsiElement): Boolean = {
      elem match {
        case _: ScReturn => true
        case m: ScMatch =>
          m.expressions.forall(checkLastReturn(_))
        case f: ScIf if f.elseExpression.isDefined && f.thenExpression.isDefined =>
          checkLastReturn(f.thenExpression.get) && checkLastReturn(f.elseExpression.get)
        case block: ScBlock if block.resultExpression.isDefined => checkLastReturn(block.resultExpression.get)
        case _ => false
      }
    }

    def returnType: Option[ScType] = {
      val fun = PsiTreeUtil.getParentOfType(elements.head, classOf[ScFunctionDefinition])
      if (fun == null) return None
      var result: Option[ScType] = None
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReturn(ret: ScReturn): Unit = {
          val newFun = PsiTreeUtil.getParentOfType(ret, classOf[ScFunctionDefinition])
          if (newFun == fun) {
            result = Some(fun.returnType.getOrElse(Unit))
          }
        }
      }
      for (element <- elements if result.isEmpty) {
        element.accept(visitor)
      }
      result
    }

    val (lastReturn, lastExprType) = elements.reverse.collectFirst {
      case expr: ScExpression => (checkLastReturn(expr), Some(expr.`type`().getOrAny))
    }.getOrElse((false, None))

    val hasReturn: Option[ScType] = returnType
    val stopAtScope = findScopeBound(elements).getOrElse(file)
    val siblings = getSiblings(elements.head, stopAtScope)
    if (siblings.isEmpty) {
      showErrorHint(ScalaBundle.message("extract.method.cannot.find.possible.scope"), REFACTORING_NAME)
      return
    }

    siblings match {
      case Seq(firstSibling, _*) if ApplicationManager.getApplication.isUnitTestMode =>
        val targetOffset = Option(dataContext).flatMap(_.getData(ChosenTargetScopeKey).toOption)
        val targetScope = targetOffset
          .flatMap(smallestScopeEnclosingTarget(siblings))
          .getOrElse(firstSibling)
        invokeDialog(
          elements, hasReturn, lastReturn, targetScope,
          smallestScope = siblings.length == 1,
          lastExprType  = lastExprType
        )
      case Seq() =>
      case Seq(sibling) =>
        invokeDialog(elements, hasReturn, lastReturn, sibling, smallestScope = true, lastExprType)
      case siblings =>
        showPsiChooser(siblings, { (selectedValue: PsiElement) =>
          invokeDialog(
            elements, hasReturn, lastReturn, selectedValue,
            smallestScope = siblings.last == selectedValue,
            lastExprType  = lastExprType
          )
        }, ScalaBundle.message("choose.level.for.extract.method"), getTextForElement, _.getParent)
    }
  }

  def smallestScopeEnclosingTarget(scopeElements: Seq[PsiElement])(targetOffset: Int): Option[PsiElement] =
    scopeElements
        .filter(_.getContext.getTextRange.containsOffset(targetOffset))
        .minByOption(_.getContext.getTextLength)

  private def getSiblings(element: PsiElement, @Nullable stopAtScope: PsiElement): Seq[PsiElement] = {
    def isInScala2(file: PsiFile): Boolean =
      file.getViewProvider.getBaseLanguage == ScalaLanguage.INSTANCE

    def isParentOk(prev: PsiElement, parent: PsiElement): Boolean = (prev, parent) match {
      case (_, null)                                                                    => false
      case (_, file: ScalaFile)               if isInScala2(file)                       => false
      case (_, pkg: ScPackaging)              if pkg.containingFile.forall(isInScala2)  => false
      case (p: ScPackaging, _: ScalaFile)     if !p.isExplicit                          => false
      case (p1: ScPackaging, p2: ScPackaging) if !p1.isExplicit && !p2.isExplicit       => false
      case _ =>
        assert(parent.getTextRange != null, "TextRange is null: " + parent.getText)
        stopAtScope == null || stopAtScope.getTextRange.contains(parent.getTextRange)
    }

    var res    = List.empty[PsiElement]
    var prev   = element
    var parent = element.getParent

    while (isParentOk(prev, parent)) {
      if (parent.is[ScalaFile, ScPackaging, ScBlock, ScTemplateBody])
        res ::= prev

      prev   = parent
      parent = parent match {
        case _: ScalaFile => null
        case _            => parent.getParent
      }
    }
    res
  }

  private def findScopeBound(elements: Seq[PsiElement]): Option[PsiElement] = {
    val commonParent = PsiTreeUtil.findCommonParent(elements: _*)

    def scopeBound(ref: ScReference): Option[PsiElement] = {
      val fromThisRef: Option[ScTemplateDefinition] = ref.qualifier match {
        case Some(thisRef: ScThisReference) => thisRef.refTemplate
        case Some(_) => return None
        case None => None
      }
      val defScope: Option[PsiElement] = fromThisRef.orElse {
        ref.resolve() match {
          case primConstr: ScPrimaryConstructor =>
            primConstr.containingClass match {
              case clazz: ScClass =>
                if (clazz.isLocal) clazz.parent
                else clazz.containingClass.toOption
              case _ => None
            }
          case member: ScMember if member.isDefinedInClass => member.containingClass.toOption
          case td: ScTypeDefinition => td.parent
          case ScalaPsiUtil.inNameContext(varDef: ScVariableDefinition)
            if ScalaPsiUtil.isLValue(ref) && !elements.exists(_.isAncestorOf(varDef, strict = false)) =>
            varDef.parent
          case member: PsiMember => member.containingClass.toOption
          case _ => return None
        }
      }
      defScope match {
        case Some(clazz: PsiClass) =>
          commonParent.parentsInFile.collectFirst {
            case td: ScTemplateDefinition if td == clazz || td.isInheritor(clazz, true) => td
          }
        case local @ Some(_) => local
        case _ =>
          PsiTreeUtil.getParentOfType(commonParent, classOf[ScPackaging]).toOption
                  .orElse(commonParent.containingFile)
      }
    }

    var result: PsiElement = commonParent.getContainingFile
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReference): Unit = {
        scopeBound(ref) match {
          case Some(bound: PsiElement) if PsiTreeUtil.isAncestor(result, bound, true) => result = bound
          case _ =>
        }
      }
    }
    elements.foreach {
      case elem: ScalaPsiElement => elem.accept(visitor)
      case _ =>
    }
    Option(result)
  }


  private def invokeDialog(elements: Seq[PsiElement], hasReturn: Option[ScType],
                           lastReturn: Boolean, sibling: PsiElement, smallestScope: Boolean,
                           lastExprType: Option[ScType])
                          (implicit project: Project, editor: Editor): Unit = {

    val info = ReachingDefinitionsCollector.collectVariableInfo(elements, sibling)

    val input = info.inputVariables
    val output = info.outputVariables
    if (output.exists(_.element.is[ScFunctionDefinition])) {
      showErrorHint(ScalaBundle.message("cannot.extract.used.function.definition"), REFACTORING_NAME)
      return
    }
    val settings: ScalaExtractMethodSettings =
      if (!ApplicationManager.getApplication.isUnitTestMode) {
        val dialog = new ScalaExtractMethodDialog(project, elements.toArray, hasReturn, lastReturn, sibling,
          input.toArray, output.toArray, lastExprType)
        dialog.show()
        if (!dialog.isOK) return
        dialog.getSettings
      }
      else {
        val innerClassSettings = {
          val text = editor.getDocument.getImmutableCharSequence
          val isCase = text.startsWith("//case class")
          val isInner = text.startsWith("//inner class")
          val out = output.map(ScalaExtractMethodUtils.convertVariableData(_, elements)).map(ExtractMethodOutput.from)
          InnerClassSettings(isCase || isInner, "TestMethodNameResult", out.toArray, isCase)
        }

        new ScalaExtractMethodSettings("testMethodName", ScalaExtractMethodUtils.getParameters(input, elements).toArray,
          ScalaExtractMethodUtils.getReturns(output, elements).toArray, "", sibling,
          elements.toArray, hasReturn, ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE, lastReturn, lastExprType, innerClassSettings)
      }
    val duplicates = DuplicatesUtil.findDuplicates(settings)
    performRefactoring(settings, editor, duplicates)
  }

  private def getTextForElement(element: PsiElement): String = {
    def local(text: String) = ScalaBundle.message("extract.local.method", text)
    element.getParent match {
      case tbody: ScTemplateBody =>
        PsiTreeUtil.getParentOfType(tbody, classOf[ScTemplateDefinition]) match {
          case o: ScObject => ScalaBundle.message("extract.method.to.object.name", o.name)
          case c: ScClass => ScalaBundle.message("extract.method.to.class.name", c.name)
          case t: ScTrait => ScalaBundle.message("extract.method.to.trait.name", t.name)
          case _: ScNewTemplateDefinition => ScalaBundle.message("extract.method.to.anonymous.class")
        }
      case _: ScTry => local(ScalaBundle.message("try.block"))
      case _: ScConstrBlockExpr => local(ScalaBundle.message("constructor"))
      case b: ScBlock  =>
        b.getParent match {
          case f: ScFunctionDefinition => local(ScalaBundle.message("def.name", f.name))
          case p: ScPatternDefinition if p.bindings.nonEmpty => local(ScalaBundle.message("val.name", p.bindings.head.name))
          case v: ScVariableDefinition if v.bindings.nonEmpty => local(ScalaBundle.message("var.name", v.bindings.head.name))
          case _: ScCaseClause => local(ScalaBundle.message("case.clause"))
          case ifStmt: ScIf =>
            if (ifStmt.thenExpression.contains(b)) local(ScalaBundle.message("if.block"))
            else ScalaBundle.message("extract.local.method.in.else.block")
          case forStmt: ScFor if forStmt.body.contains(b) => local(ScalaBundle.message("for.statement"))
          case whileStmt: ScWhile if whileStmt.expression.contains(b) => local(ScalaBundle.message("while.statement"))
          case doSttm: ScDo if doSttm.body.contains(b) => local(ScalaBundle.message("do.statement"))
          case funExpr: ScFunctionExpr if funExpr.result.contains(b) => local(ScalaBundle.message("function.expression"))
          case _ => local(ScalaBundle.message("code.block"))
        }
      case p: ScPackaging => ScalaBundle.message("extract.method.to.package.name", p.packageName)
      case _: ScalaFile => ScalaBundle.message("extract.file.method")
      case _ => ScalaBundle.message("unknown.extraction")
    }
  }

  private def performRefactoring(settings: ScalaExtractMethodSettings, editor: Editor, duplicates: Seq[DuplicateMatch]): Unit = {
    implicit val project: Project = editor.getProject
    if (project == null) return
    ReadAction
      .nonBlocking(() => ScalaExtractMethodUtils.createMethodFromSettings(settings))
      .finishOnUiThread(ModalityState.defaultModalityState, doPerformRefactoring(_, settings, editor, duplicates))
      .expireWhen(() => project.isDisposed)
      .submit(AppExecutorUtil.getAppExecutorService)
  }

  private def doPerformRefactoring(method: ScFunction, settings: ScalaExtractMethodSettings, editor: Editor, duplicates: Seq[DuplicateMatch])
                                  (implicit project: Project): Unit = {
    if (method == null) return
    val ics = settings.innerClassSettings

    def newLine = createNewLine()(method.getManager)

    def addElementBefore(elem: PsiElement, nextSibling: PsiElement, typeAdjuster: TypeAdjuster) = {
      val added = nextSibling.getParent.addBefore(elem, nextSibling)
      typeAdjuster.markToAdjust(added)
      added
    }

    def insertInnerClassBefore(anchorNext: PsiElement, typeAdjuster: TypeAdjuster): Unit = {
      if (!ics.needClass) return

      val classText = ics.classText(canonTextForTypes = true)
      val clazz = createTemplateDefinitionFromText(classText, anchorNext.getContext, anchorNext)
      addElementBefore(clazz, anchorNext, typeAdjuster)
      addElementBefore(newLine, anchorNext, typeAdjuster)
    }

    def insertMethod(typeAdjuster: TypeAdjuster): PsiElement = {
      var insertedMethod: PsiElement = null
      settings.nextSibling match {
        case s childOf (_: ScTemplateBody) =>
          // put the extract method *below* the current code if it is added to a template body.
          val nextSibling = s.getNextSiblingNotWhitespaceComment
          addElementBefore(newLine, nextSibling, typeAdjuster)
          insertedMethod = addElementBefore(method, nextSibling, typeAdjuster)
          addElementBefore(newLine, nextSibling, typeAdjuster)
        case s =>
          insertedMethod = addElementBefore(method, s, typeAdjuster)
          addElementBefore(newLine, s, typeAdjuster)
      }
      insertedMethod
    }

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    inWriteCommandAction {
      val typeAdjuster = new TypeAdjuster()
      val method = insertMethod(typeAdjuster)
      insertInnerClassBefore(method, typeAdjuster)

      ScalaExtractMethodUtils.replaceWithMethodCall(settings, settings.elements.toSeq, param => param.oldName, output => output.paramName, typeAdjuster)
      typeAdjuster.adjustTypes()

      CodeStyleManager.getInstance(project).reformat(method)
      editor.getSelectionModel.removeSelection()

      if (settings.returnType.isEmpty && settings.typeParameters.isEmpty && duplicates.nonEmpty) {
        DuplicatesUtil.processDuplicates(duplicates, settings)(project, editor)
      }
    }
  }
}

object ScalaExtractMethodHandler {
  // Used in ScalaExtractMethodTestBase
  // Test package doesn't match this package, so using the common parent package as a `private` scope
  @TestOnly
  private[scala] val ChosenTargetScopeKey: DataKey[Int] = DataKey.create("chosenTargetScope")
}
