package org.jetbrains.plugins.scala.lang
package refactoring.extractMethod

import _root_.com.intellij.psi._
import _root_.org.jetbrains.annotations.Nullable
import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import _root_.org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import _root_.org.jetbrains.plugins.scala.lang.resolve.StdKinds
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.{ScrollType, Editor}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.refactoring.{HelpID, RefactoringActionHandler}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefintionsCollector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.command.CommandProcessor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import collection.mutable.ArrayBuffer
import psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScPatternDefinition, ScFunction, ScFunctionDefinition}
import psi.api.{ScalaElementVisitor, ScalaRecursiveElementVisitor, ScalaFile}
import psi.api.base.patterns.ScCaseClause
import psi.types.result.TypingContext
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.plugins.scala.extensions.{toPsiElementExt, Parent, toPsiNamedElementExt, inWriteCommandAction}
import com.intellij.psi.codeStyle.CodeStyleManager
import scala.annotation.tailrec
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

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

    ScalaRefactoringUtil.afterExpressionChoosing(project, editor, file, dataContext, REFACTORING_NAME, ScalaRefactoringUtil.checkCanBeIntroduced(_)) {
      invokeOnEditor(project, editor, file.asInstanceOf[ScalaFile], dataContext)
    }
  }

  private def invokeOnEditor(project: Project, editor: Editor, file: ScalaFile, dataContext: DataContext) {
    if (!ScalaRefactoringUtil.ensureFileWritable(project, file)) {
      showErrorMessage(ScalaBundle.message("file.is.not.writable"), project, editor)
      return
    }
    if (!editor.getSelectionModel.hasSelection) return
    ScalaRefactoringUtil.trimSpacesAndComments(editor, file, trimComments = false)
    val startElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionStart)
    val endElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionEnd - 1)
    val elements = ScalaPsiUtil.getElementsRange(startElement, endElement).toArray match {
      case Array(b: ScBlock) if !b.hasRBrace => b.children.toArray
      case elems => elems
    }
    def acceptable(elem: PsiElement): Boolean = elem match {
      case _: ScBlockStatement => true
      case comm: PsiComment if !comm.getParent.isInstanceOf[ScMember] => true
      case _: PsiWhiteSpace => true
      case _ if ScalaTokenTypes.tSEMICOLON == elem.getNode.getElementType => true
      case _ => false
    }
    if (elements.length == 0 || !elements.forall(acceptable) || !elements.exists(_.isInstanceOf[ScBlockStatement])) {
      showErrorMessage(ScalaBundle.message("cannot.extract.empty.message"), project, editor)
      return
    }

    def checkLastReturn(elem: PsiElement): Boolean = {
      elem match {
        case ret: ScReturnStmt => true
        case m: ScMatchStmt =>
          m.getBranches.forall(checkLastReturn(_))
        case f: ScIfStmt if f.elseBranch != None && f.thenBranch != None =>
          checkLastReturn(f.thenBranch.get) && checkLastReturn(f.elseBranch.get)
        case block: ScBlock if block.lastExpr != None => checkLastReturn(block.lastExpr.get)
        case _ => false
      }
    }
    @tailrec
    def checkLastExpressionMeaningful(elem: PsiElement): Boolean = {
      if (!elem.isInstanceOf[ScExpression]) return false
      val expr = elem.asInstanceOf[ScExpression]
      expr.getParent match {
        case t: ScTryBlock if t.lastExpr == Some(expr) => checkLastExpressionMeaningful(t.getParent)
        case bl: ScBlock if bl.lastExpr == Some(expr) => checkLastExpressionMeaningful(bl)
        case bl: ScBlock => false
        case clause: ScCaseClause => checkLastExpressionMeaningful(clause.getParent.getParent)
        case d: ScDoStmt if d.getExprBody == Some(expr) => false
        case w: ScWhileStmt if w.body == Some(expr) => false
        case i: ScIfStmt if i.elseBranch == None && i.thenBranch == Some(expr) => false
        case fun: ScFunction if !fun.hasAssign => false
        case _ => true
      }
    }
    var i = elements.length - 1
    while (!elements(i).isInstanceOf[ScalaPsiElement] && i > 0) i = i -1
    val lastReturn = checkLastReturn(elements(i))
    val isLastExpressionMeaningful: Option[ScType] = {
      if (lastReturn) None
      else if (checkLastExpressionMeaningful(elements(i)))
        Some(elements(i).asInstanceOf[ScExpression].getType(TypingContext.empty).getOrAny)
      else None
    }
    var hasReturn: Option[ScType] = None
    val fun = PsiTreeUtil.getParentOfType(elements(0), classOf[ScFunctionDefinition])
    for (element <- elements if fun != null && hasReturn == None) {
      if (element.isInstanceOf[ScSelfInvocation]) {
        showErrorMessage(ScalaBundle.message("cannot.extract.self.invocation"), project, editor)
        return
      }

      def checkReturn(ret: ScReturnStmt) {
        val newFun = PsiTreeUtil.getParentOfType(ret, classOf[ScFunctionDefinition])
        if (newFun == fun) {
          hasReturn = Some(fun.returnType.getOrElse(psi.types.Unit: ScType))
        }
      }
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReturnStatement(ret: ScReturnStmt) {
          checkReturn(ret)
        }
      }
      element match {
        case element: ScalaPsiElement => visitor.visitElement(element)
        case _ =>
      }
      element match {
        case returnStmt: ScReturnStmt => checkReturn(returnStmt)
        case _ =>
      }
    }
    var stopAtScope: PsiElement = null
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
        def attachElement(elem: PsiElement) {
          if (elem.getContainingFile != ref.getContainingFile) return
          if (elem.getTextOffset >= elements(0).getTextRange.getStartOffset &&
              elem.getTextOffset <= elements(elements.length - 1).getTextRange.getEndOffset) return
          if (stopAtScope == null) stopAtScope = elem.getParent
          else {
            if (stopAtScope.getTextRange.contains(elem.getTextRange)) stopAtScope = elem.getParent
          }
        }
        ref.resolve() match {
          case td: ScTypeDefinition => attachElement(td)
          case fun: ScFunction if fun.typeParameters.length > 0 => attachElement(fun)
          case _ =>
        }
      }
    }
    for (element <- elements if element.isInstanceOf[ScalaPsiElement])
      element.asInstanceOf[ScalaPsiElement].accept(visitor: ScalaElementVisitor)
    if (stopAtScope == null) stopAtScope = file
    val siblings: Array[PsiElement] = getSiblings(elements(0), stopAtScope)
    if (siblings.length == 0) return
    if (ApplicationManager.getApplication.isUnitTestMode && siblings.length > 0) {
      invokeDialog(project, editor, elements, hasReturn, lastReturn, siblings(0), siblings.length == 1,
        isLastExpressionMeaningful)
    } else if (siblings.length > 1) {
      ScalaRefactoringUtil.showChooser(editor, siblings, {selectedValue =>
        invokeDialog(project, editor, elements, hasReturn, lastReturn, selectedValue,
          siblings(siblings.length - 1) == selectedValue, isLastExpressionMeaningful)
      }, "Choose level for Extract Method", getTextForElement, true)
      return
    }
    else if (siblings.length == 1) {
      invokeDialog(project, editor, elements, hasReturn, lastReturn, siblings(0), smallestScope = true, isLastExpressionMeaningful)
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

  private def invokeDialog(project: Project, editor: Editor, elements: Array[PsiElement], hasReturn: Option[ScType],
                           lastReturn: Boolean, sibling: PsiElement, smallestScope: Boolean,
                           lastMeaningful: Option[ScType]) {

    val info = ReachingDefintionsCollector.collectVariableInfo(elements.toSeq, sibling.asInstanceOf[ScalaPsiElement])

    val input = info.inputVariables
    val output = info.outputVariables
    val settings: ScalaExtractMethodSettings = if (!ApplicationManager.getApplication.isUnitTestMode) {

      val dialog = new ScalaExtractMethodDialog(project, elements, hasReturn, lastReturn, sibling, sibling,
        input.toArray, output.toArray, lastMeaningful)
      dialog.show()
      if (!dialog.isOK) {
        return
      }
      dialog.getSettings
    } else {
      new ScalaExtractMethodSettings("testMethodName", ScalaExtractMethodUtils.getParameters(input.toArray, elements),
        ScalaExtractMethodUtils.getReturns(output.toArray, elements), "", sibling, sibling,
        elements, hasReturn, lastReturn, lastMeaningful)
    }
    performRefactoring(settings, editor)
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
          case p: ScPatternDefinition if p.bindings.nonEmpty => local(s"val ${p.bindings(0).name}")
          case v: ScVariableDefinition if v.bindings.nonEmpty => local(s"var ${v.bindings(0).name}")
          case _: ScCaseClause => local("case clause")
          case ifStmt: ScIfStmt =>
            if (ifStmt.thenBranch.exists(_ == b)) local("if block")
            else "Extract local method in else block"
          case forStmt: ScForStatement if forStmt.body.exists(_ == b) => local("for statement")
          case whileStmt: ScWhileStmt if whileStmt.body.exists(_ == b) => local("while statement")
          case doSttm: ScDoStmt if doSttm.getExprBody.exists(_ == b) => local("do statement")
          case funExpr: ScFunctionExpr if funExpr.result.exists(_ == b) => local("function expression")
          case _ => local("code block")
        }
      case _: ScalaFile => "Extract file method"
      case _ => "Unknown extraction"
    }
  }

  private def performRefactoring(settings: ScalaExtractMethodSettings, editor: Editor) {


    val method = ScalaExtractMethodUtils.createMethodFromSettings(settings)
    val element = settings.elements.find(elem => elem.isInstanceOf[ScalaPsiElement]).getOrElse(return)
    val processor = new CompletionProcessor(StdKinds.refExprLastRef, element, includePrefixImports = false)
    PsiTreeUtil.treeWalkUp(processor, element, null, ResolveState.initial)
    val allNames = new mutable.HashSet[String]()
    allNames ++= processor.candidatesS.map(rr => rr.element.name)
    def generateFreshName(s: String): String = {
      var freshName = s
      var count = 0
      while (allNames.contains(freshName)) {
        count += 1
        freshName = s + count
      }
      freshName
    }
    val rFreshName = generateFreshName("r")
    val tFreshName = generateFreshName("t")
    val xFreshName = generateFreshName("x")

    def getMatchForReturn(s: String): ScExpression = {
      val exprText = settings.returnType match {
        case Some(psi.types.Unit) => "if (" + s + ") return"
        case Some(tp) => s + " match {\n" +
                "  case Some(" + xFreshName + ") => return " + xFreshName + "\n  case None =>\n}"
        case None => s
      }
      ScalaPsiElementFactory.createExpressionFromText(exprText, method.getManager)
    }

    if (method == null) return

    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)

    inWriteCommandAction(editor.getProject, REFACTORING_NAME) {

      settings.nextSibling match {
        case s@Parent(_: ScTemplateBody) =>
          // put the extract method *below* the current code if it is added to a template body.
          val nextSibling = s.getNextSiblingNotWhitespaceComment
          s.getParent.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(method.getManager), nextSibling.getNode)
          s.getParent.getNode.addChild(method.getNode, nextSibling.getNode)
          s.getParent.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(method.getManager), nextSibling.getNode)
        case s =>
          s.getParent.getNode.addChild(method.getNode, s.getNode)
          s.getParent.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(method.getManager), s.getNode)
      }
      val methodCall = new StringBuilder(settings.methodName)
      if (settings.parameters.exists(p => p.passAsParameter)) {
        val paramStrings = new ArrayBuffer[String]
        for (param <- settings.parameters if param.passAsParameter) {
          paramStrings += param.oldName + (if (param.isFunction) " _" else "")
        }
        methodCall.append(paramStrings.mkString("(", ", ", ")"))
      }
      if (settings.lastReturn) {
        val expr = ScalaPsiElementFactory.createExpressionFromText("return " + methodCall.toString, method.getManager)
        settings.elements.apply(0).replace(expr)
      } else if (settings.returns.length == 0) {
        settings.elements.apply(0).replace(getMatchForReturn(methodCall.toString()))
      } else if (settings.returns.length == 1) {
        val ret = settings.returns.apply(0)
        val exprText = if (settings.returnType == None) methodCall.toString() else tFreshName + "._2.get"
        val stmt: PsiElement = if (ret.needNewDefinition) {
          ScalaPsiElementFactory.createDeclaration(ret.returnType, ret.oldParamName, !ret.isVal,
            exprText, method.getManager, isPresentableText = true)
        } else {
          ScalaPsiElementFactory.createExpressionFromText(ret.oldParamName + " = " +
                  exprText, settings.elements.apply(0).getManager)
        }
        val before = settings.elements.apply(0)
        def addNode(elem: PsiElement) {before.getParent.getNode.addChild(elem.getNode, before.getNode)}
        if (settings.returnType != None) {
          val decl = ScalaPsiElementFactory.createDeclaration(null, tFreshName, isVariable = false, methodCall.toString(), method.getManager, isPresentableText = true)
          val nl1 = ScalaPsiElementFactory.createNewLineNode(decl.getManager).getPsi
          val nl2 = ScalaPsiElementFactory.createNewLineNode(decl.getManager).getPsi
          val matcher = getMatchForReturn(tFreshName + "._1")
          addNode(decl); addNode(nl2); addNode(matcher); addNode(nl1)
        }
        addNode(stmt)
        before.getParent.getNode.removeChild(before.getNode)
      } else {
        val exprText = if (settings.returnType == None) methodCall.toString() else tFreshName + "._2.get"
        val stmt = ScalaPsiElementFactory.createDeclaration(null, rFreshName, isVariable = false, exprText, method.getManager, isPresentableText = true)
        val before = settings.elements.apply(0)
        def addNodeBack(elem: PsiElement) {before.getParent.getNode.addChild(elem.getNode, before.getNode)}
        if (settings.returnType != None) {
          val decl = ScalaPsiElementFactory.createDeclaration(null, tFreshName, isVariable = false, methodCall.toString(), method.getManager, isPresentableText = true)
          val nl1 = ScalaPsiElementFactory.createNewLineNode(decl.getManager).getPsi
          val nl2 = ScalaPsiElementFactory.createNewLineNode(decl.getManager).getPsi
          val matcher = getMatchForReturn(tFreshName + "._1")
          addNodeBack(decl); addNodeBack(nl2); addNodeBack(matcher); addNodeBack(nl1)
        }
        addNodeBack(stmt)
        before.getParent.getNode.removeChild(before.getNode)
        var lastElem: PsiElement = stmt
        def addNode(elem: PsiElement) {
          lastElem.getParent.getNode.addChild(elem.getNode, lastElem.getNextSibling.getNode)
          lastElem = elem
        }
        var count = 1
        for (ret <- settings.returns) {
          addNode(ScalaPsiElementFactory.createNewLineNode(stmt.getManager).getPsi)
          if (ret.needNewDefinition) {
            addNode(ScalaPsiElementFactory.createDeclaration(ret.returnType, ret.oldParamName,
              !ret.isVal, rFreshName + "._" + count, lastElem.getManager, isPresentableText = true))
          } else {
            addNode(ScalaPsiElementFactory.createExpressionFromText(ret.oldParamName + " = " + rFreshName +
                    "._" + count, stmt.getManager))
          }
          count += 1
        }
      }
      var i = 1
      while (i < settings.elements.length) {
        settings.elements.apply(i).getParent.getNode.removeChild(settings.elements.apply(i).getNode)
        i = i + 1
      }

      ScalaPsiUtil.adjustTypes(method)

      val manager = CodeStyleManager.getInstance(method.getProject)
      manager.reformat(method)
      editor.getSelectionModel.removeSelection()
    }
  }

  private def showErrorMessage(text: String, project: Project, editor: Editor) {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    CommonRefactoringUtil.showErrorHint(project, editor, text, REFACTORING_NAME, HelpID.EXTRACT_METHOD)
  }
}