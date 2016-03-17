package org.jetbrains.plugins.scala
package lang
package refactoring.extractMethod

import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefintionsCollector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil, TypeAdjuster}
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates.DuplicatesUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.showErrorHint
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */
class ScalaExtractMethodHandler extends RefactoringActionHandler {
  private val REFACTORING_NAME: String = ScalaBundle.message("extract.method.title")

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {/*do nothing*/}

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    if (!file.isInstanceOf[ScalaFile]) return

    UsageTrigger.trigger(ScalaBundle.message("extract.method.id"))

    ScalaRefactoringUtil.afterExpressionChoosing(project, editor, file, dataContext, REFACTORING_NAME, ScalaRefactoringUtil.checkCanBeIntroduced(_)) {
      invokeOnEditor(project, editor, file.asInstanceOf[ScalaFile], dataContext)
    }
  }

  private def invokeOnEditor(project: Project, editor: Editor, file: ScalaFile, dataContext: DataContext)
                            (implicit typeSystem: TypeSystem = project.typeSystem) {
    if (!ScalaRefactoringUtil.ensureFileWritable(project, file)) {
      showErrorHint(ScalaBundle.message("file.is.not.writable"), project, editor, REFACTORING_NAME)
      return
    }
    if (!editor.getSelectionModel.hasSelection) return
    val elements: Seq[PsiElement] = ScalaRefactoringUtil.selectedElements(editor, file, trimComments = false)

    val hasWarnings = ScalaRefactoringUtil.showNotPossibleWarnings(elements, project, editor, REFACTORING_NAME)
    if (hasWarnings) return

    def checkLastReturn(elem: PsiElement): Boolean = {
      elem match {
        case ret: ScReturnStmt => true
        case m: ScMatchStmt =>
          m.getBranches.forall(checkLastReturn(_))
        case f: ScIfStmt if f.elseBranch.isDefined && f.thenBranch.isDefined =>
          checkLastReturn(f.thenBranch.get) && checkLastReturn(f.elseBranch.get)
        case block: ScBlock if block.lastExpr.isDefined => checkLastReturn(block.lastExpr.get)
        case _ => false
      }
    }

    def returnType: Option[ScType] = {
      val fun = PsiTreeUtil.getParentOfType(elements.head, classOf[ScFunctionDefinition])
      if (fun == null) return None
      var result: Option[ScType] = None
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReturnStatement(ret: ScReturnStmt) {
          val newFun = PsiTreeUtil.getParentOfType(ret, classOf[ScFunctionDefinition])
          if (newFun == fun) {
            result = Some(fun.returnType.getOrElse(psi.types.Unit))
          }
        }
      }
      for (element <- elements if result.isEmpty) {
        element.accept(visitor)
      }
      result
    }

    val (lastReturn, lastExprType) = elements.reverse.collectFirst {
      case expr: ScExpression => (checkLastReturn(expr), Some(expr.getType(TypingContext.empty).getOrAny))
    }.getOrElse((false, None))

    val hasReturn: Option[ScType] = returnType
    val stopAtScope: PsiElement = findScopeBound(elements).getOrElse(file)
    val siblings: Array[PsiElement] = getSiblings(elements.head, stopAtScope)
    if (siblings.length == 0) {
      showErrorHint(ScalaBundle.message("extract.method.cannot.find.possible.scope"), project, editor, REFACTORING_NAME)
      return
    }
    val array = elements.toArray
    if (ApplicationManager.getApplication.isUnitTestMode && siblings.length > 0) {
      invokeDialog(project, editor, array, hasReturn, lastReturn, siblings(0), siblings.length == 1,
        lastExprType)
    } else if (siblings.length > 1) {
      ScalaRefactoringUtil.showChooser(editor, siblings, {(selectedValue: PsiElement) =>
        invokeDialog(project, editor, array, hasReturn, lastReturn, selectedValue,
          siblings(siblings.length - 1) == selectedValue, lastExprType)
      }, "Choose level for Extract Method", getTextForElement, (e: PsiElement) => e.getParent)
    }
    else if (siblings.length == 1) {
      invokeDialog(project, editor, array, hasReturn, lastReturn, siblings(0), smallestScope = true, lastExprType)
    }
  }

  private def getSiblings(element: PsiElement, @Nullable stopAtScope: PsiElement): Array[PsiElement] = {
    def isParentOk(parent: PsiElement): Boolean = {
      if (parent == null) return false
      assert(parent.getTextRange != null, "TextRange is null: " + parent.getText)
      stopAtScope == null || stopAtScope.getTextRange.contains(parent.getTextRange)
    }

    val res = new ArrayBuffer[PsiElement]
    var prev = element
    var parent = element.getParent
    while (isParentOk(parent)) {
      parent match {
        case file: ScalaFile if file.isScriptFile() => res += prev
        case block: ScBlock => res += prev
        case templ: ScTemplateBody => res += prev
        case _ =>
      }
      prev = parent
      parent = parent match {
        case file: ScalaFile =>
          null
        case _ => parent.getParent
      }
    }
    res.toArray.reverse
  }

  private def findScopeBound(elements: Seq[PsiElement]): Option[PsiElement] = {
    val commonParent = PsiTreeUtil.findCommonParent(elements: _*)

    def scopeBound(ref: ScReferenceElement): Option[PsiElement] = {
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
          case member: ScMember if !member.isLocal => member.containingClass.toOption
          case td: ScTypeDefinition => td.parent
          case ScalaPsiUtil.inNameContext(varDef: ScVariableDefinition)
            if ScalaPsiUtil.isLValue(ref) && !elements.exists(_.isAncestorOf(varDef)) => varDef.parent
          case member: PsiMember => member.containingClass.toOption
          case _ => return None
        }
      }
      defScope match {
        case Some(clazz: PsiClass) =>
          commonParent.parentsInFile.collectFirst {
            case td: ScTemplateDefinition if td == clazz || td.isInheritor(clazz, deep = true) => td
          }
        case local @ Some(_) => local
        case _ =>
          PsiTreeUtil.getParentOfType(commonParent, classOf[ScPackaging]).toOption
                  .orElse(commonParent.containingFile)
      }
    }

    var result: PsiElement = commonParent.getContainingFile
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
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


  private def invokeDialog(project: Project, editor: Editor, elements: Array[PsiElement], hasReturn: Option[ScType],
                           lastReturn: Boolean, sibling: PsiElement, smallestScope: Boolean,
                           lastExprType: Option[ScType])
                          (implicit typeSystem: TypeSystem) {

    val info = ReachingDefintionsCollector.collectVariableInfo(elements, sibling)

    val input = info.inputVariables
    val output = info.outputVariables
    if (output.exists(_.element.isInstanceOf[ScFunctionDefinition])) {
      showErrorHint(ScalaBundle.message("cannot.extract.used.function.definition"), project, editor, REFACTORING_NAME)
      return
    }
    val settings: ScalaExtractMethodSettings =
      if (!ApplicationManager.getApplication.isUnitTestMode) {
        val dialog = new ScalaExtractMethodDialog(project, elements, hasReturn, lastReturn, sibling,
          input.toArray, output.toArray, lastExprType)
        dialog.show()
        if (!dialog.isOK) return
        dialog.getSettings
      }
      else {
        val innerClassSettings = {
          val text = editor.getDocument.getText
          val isCase = text.startsWith("//case class")
          val isInner = text.startsWith("//inner class")
          val out = output.map(ScalaExtractMethodUtils.convertVariableData(_, elements)).map(ExtractMethodOutput.from)
          InnerClassSettings(isCase || isInner, "TestMethodNameResult", out.toArray, isCase)
        }

        new ScalaExtractMethodSettings("testMethodName", ScalaExtractMethodUtils.getParameters(input.toArray, elements),
          ScalaExtractMethodUtils.getReturns(output.toArray, elements), "", sibling,
          elements, hasReturn, lastReturn, lastExprType, innerClassSettings)
      }
    val duplicates = DuplicatesUtil.findDuplicates(settings)
    performRefactoring(settings, editor)
    if (settings.returnType.isEmpty && settings.typeParameters.isEmpty) {
      if (duplicates.nonEmpty) DuplicatesUtil.processDuplicates(duplicates, settings, project, editor)
    }
  }

  private def getTextForElement(element: PsiElement): String = {
    def local(text: String) = ScalaBundle.message("extract.local.method", text)
    element.getParent match {
      case tbody: ScTemplateBody =>
        PsiTreeUtil.getParentOfType(tbody, classOf[ScTemplateDefinition]) match {
          case o: ScObject => s"Extract method to object ${o.name}"
          case c: ScClass => s"Extract method to class ${c.name}"
          case t: ScTrait => s"Extract method to trait ${t.name}"
          case n: ScNewTemplateDefinition => "Extract method to anonymous class"
        }
      case _: ScTryBlock => local("try block")
      case _: ScConstrBlock => local("constructor")
      case b: ScBlock  =>
        b.getParent match {
          case f: ScFunctionDefinition => local(s"def ${f.name}")
          case p: ScPatternDefinition if p.bindings.nonEmpty => local(s"val ${p.bindings.head.name}")
          case v: ScVariableDefinition if v.bindings.nonEmpty => local(s"var ${v.bindings.head.name}")
          case _: ScCaseClause => local("case clause")
          case ifStmt: ScIfStmt =>
            if (ifStmt.thenBranch.contains(b)) local("if block")
            else "Extract local method in else block"
          case forStmt: ScForStatement if forStmt.body.contains(b) => local("for statement")
          case whileStmt: ScWhileStmt if whileStmt.body.contains(b) => local("while statement")
          case doSttm: ScDoStmt if doSttm.getExprBody.contains(b) => local("do statement")
          case funExpr: ScFunctionExpr if funExpr.result.contains(b) => local("function expression")
          case _ => local("code block")
        }
      case _: ScalaFile => "Extract file method"
      case _ => "Unknown extraction"
    }
  }

  private def performRefactoring(settings: ScalaExtractMethodSettings, editor: Editor)
                                (implicit typeSystem: TypeSystem) {
    val method = ScalaExtractMethodUtils.createMethodFromSettings(settings)
    if (method == null) return
    val ics = settings.innerClassSettings

    def newLine = ScalaPsiElementFactory.createNewLine(method.getManager)

    def addElementBefore(elem: PsiElement, nextSibling: PsiElement) = {
      val added = nextSibling.getParent.addBefore(elem, nextSibling)
      TypeAdjuster.markToAdjust(added)
      added
    }

    def insertInnerClassBefore(anchorNext: PsiElement) {
      if (!ics.needClass) return

      val classText = ics.classText(canonTextForTypes = true)
      val clazz = ScalaPsiElementFactory.createTemplateDefinitionFromText(classText, anchorNext.getContext, anchorNext)
      addElementBefore(clazz, anchorNext)
      addElementBefore(newLine, anchorNext)
    }

    def insertMethod() = {
      var insertedMethod: PsiElement = null
      settings.nextSibling match {
        case s childOf (_: ScTemplateBody) =>
          // put the extract method *below* the current code if it is added to a template body.
          val nextSibling = s.getNextSiblingNotWhitespaceComment
          addElementBefore(newLine, nextSibling)
          insertedMethod = addElementBefore(method, nextSibling)
          addElementBefore(newLine, nextSibling)
        case s =>
          insertedMethod = addElementBefore(method, s)
          addElementBefore(newLine, s)
      }
      insertedMethod
    }

    def insertMethodCall(implicit typeSystem: TypeSystem) =
      ScalaExtractMethodUtils.replaceWithMethodCall(settings, settings.elements, param => param.oldName, output => output.paramName)


    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)

    inWriteCommandAction(editor.getProject, REFACTORING_NAME) {

      val method = insertMethod()
      insertInnerClassBefore(method)
      insertMethodCall

      val manager = CodeStyleManager.getInstance(method.getProject)
      manager.reformat(method)
      editor.getSelectionModel.removeSelection()
    }
  }
}