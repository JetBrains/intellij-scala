package org.jetbrains.plugins.scala.lang
package refactoring.extractMethod

import _root_.com.intellij.psi._
import _root_.org.jetbrains.annotations.Nullable
import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import _root_.org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import _root_.org.jetbrains.plugins.scala.lang.resolve.{StdKinds}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.{ScrollType, Editor}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.refactoring.util.RefactoringMessageDialog
import com.intellij.refactoring.{HelpID, RefactoringActionHandler}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefintionsCollector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Pass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScDeclarationSequenceHolder, ScalaPsiUtil}
import collection.mutable.{HashSet, ArrayBuffer}
import psi.api.base.ScReferenceElement
import psi.api.toplevel.typedef.{ScTypeDefinition, ScMember}
import psi.api.statements.{ScTypeAlias, ScFunction, ScFunctionDefinition}
import psi.api.{ScalaElementVisitor, ScalaRecursiveElementVisitor, ScalaFile}

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */
class ScalaExtractMethodHandler extends RefactoringActionHandler {
  private val REFACTORING_NAME: String = ScalaBundle.message("extract.method.title")

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext): Unit = {/*do nothing*/}

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext): Unit = {
    editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    if (!file.isInstanceOf[ScalaFile]) return
    if (!editor.getSelectionModel.hasSelection) {
      editor.getSelectionModel.selectLineAtCaret
    }

    invokeOnEditor(project, editor, file.asInstanceOf[ScalaFile], dataContext)
  }

  private def invokeOnEditor(project: Project, editor: Editor, file: ScalaFile, dataContext: DataContext): Unit = {
    if (!editor.getSelectionModel.hasSelection) return
    ScalaRefactoringUtil.trimSpacesAndComments(editor, file, false)
    val startElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionStart)
    val endElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionEnd - 1)
    val elements = ScalaPsiUtil.getElementsRange(startElement, endElement).toArray
    if (elements.length == 0) {
      showErrorMessage(ScalaBundle.message("cannot.extract.empty.message"), project)
      return
    }


    def checkLastReturn(elem: PsiElement): Boolean = {
      elem match {
        case ret: ScReturnStmt => true
        case m: ScMatchStmt => {
          m.getBranches.forall(checkLastReturn(_))
        }
        case f: ScIfStmt if f.elseBranch != None && f.thenBranch != None => {
          checkLastReturn(f.thenBranch.get) && checkLastReturn(f.elseBranch.get)
        }
        case block: ScBlock if block.lastExpr != None => checkLastReturn(block.lastExpr.get)
        case _ => false
      }
    }
    var i = elements.length - 1
    while (!elements(i).isInstanceOf[ScalaPsiElement] && i > 0) i = i -1
    val lastReturn = checkLastReturn(elements(i))
    var hasReturn: Option[ScType] = None
    val fun = PsiTreeUtil.getParentOfType(elements(0), classOf[ScFunctionDefinition])
    for (element <- elements if fun != null && hasReturn == None) {
      if (element.isInstanceOf[ScSelfInvocation]) {
        showErrorMessage(ScalaBundle.message("cannot.extract.self.invocation"), project)
        return
      }

      def checkReturn(ret: ScReturnStmt): Unit = {
        val newFun = PsiTreeUtil.getParentOfType(ret, classOf[ScFunctionDefinition])
        if (newFun == fun) {
          hasReturn = Some(fun.returnType.getOrElse(psi.types.Unit: ScType))
        }
      }
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReturnStatement(ret: ScReturnStmt) = {
          checkReturn(ret)
        }
      }
      if (element.isInstanceOf[ScalaPsiElement])
       visitor.visitElement(element.asInstanceOf[ScalaPsiElement])
      if (element.isInstanceOf[ScReturnStmt]) {
        checkReturn(element.asInstanceOf[ScReturnStmt])
      }
    }
    var stopAtScope: PsiElement = null
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) = {
        def attachElement(elem: PsiElement) {
          if (elem.getContainingFile != ref.getContainingFile) return
          if (elem.getTextOffset >= elements(0).getTextRange.getStartOffset &&
              elem.getTextOffset <= elements(elements.length - 1).getTextRange.getEndOffset) return
          if (stopAtScope == null) stopAtScope = elem.getParent
          else {
            if (stopAtScope.getTextRange.contains(elem.getTextRange)) stopAtScope = elem.getParent
          }
        }
        ref.resolve match {
          case td: ScTypeDefinition => attachElement(td)
          case fun: ScFunction if fun.typeParameters.length > 0 => attachElement(fun)
          case _ =>
        }
      }
    }
    for (element <- elements if element.isInstanceOf[ScalaPsiElement])
      element.asInstanceOf[ScalaPsiElement].accept(visitor: ScalaElementVisitor)
    val siblings: Array[PsiElement] = getSiblings(elements(0), stopAtScope)
    val scope: PsiElement = {
      if (file.isScriptFile()) {
        file
      } else if (siblings.length > 0) {
        val lastSibling = siblings(0)
        val element = elements(0)
        var parent = element.getParent
        var res: PsiElement = null
        while (parent != lastSibling && parent != null) {
          parent match {
            case bl: ScBlock => res = bl
            case _ =>
          }

          parent = parent.getParent
        }
        res
      } else null
    }
    if (scope == null) return
    if (siblings.length == 0) return
    if (ApplicationManager.getApplication.isUnitTestMode && siblings.length > 0) {
      invokeDialog(project, editor, elements, hasReturn, lastReturn, siblings(0), siblings.length == 1)
    } else if (siblings.length > 1) {
      ScalaRefactoringUtil.showChooser(editor, siblings, {selectedValue =>
        invokeDialog(project, editor, elements, hasReturn, lastReturn, selectedValue,
          siblings(siblings.length - 1) == selectedValue)
      }, "Choose level for Extract Method", getTextForElement _)
      return
    }
    else if (siblings.length == 1) {
      invokeDialog(project, editor, elements, hasReturn, lastReturn, siblings(0), true)
    }
  }

  private def getSiblings(element: PsiElement, @Nullable stopAtScope: PsiElement): Array[PsiElement] = {
    def isParentOk(parent: PsiElement): Boolean = {
      parent != null && (stopAtScope == null || stopAtScope.getTextRange.contains(parent.getTextRange))
    }

    val res = new ArrayBuffer[PsiElement]
    var prev = element
    var parent = element.getParent
    while (isParentOk(parent)) {
      parent match {
        case file: ScalaFile if file.isScriptFile() => res += prev
        case block: ScBlockExpr => res += prev
        case tryBlcok: ScTryBlock => res += prev
        case templ: ScTemplateBody => res += prev
        case _ =>
      }
      prev = parent
      parent = parent.getParent
    }
    return res.toArray.reverse
  }

  private def invokeDialog(project: Project, editor: Editor, elements: Array[PsiElement], hasReturn: Option[ScType],
                           lastReturn: Boolean, sibling: PsiElement, smallestScope: Boolean) {
    val scope = sibling.getParent
    val info =
    if (!smallestScope)
      ReachingDefintionsCollector.collectVariableInfo(elements.toSeq, scope.asInstanceOf[ScalaPsiElement])
    else
      ReachingDefintionsCollector.collectVariableInfo(elements.toSeq)
    val input = info.inputVariables
    val output = info.outputVariables
    val settings: ScalaExtractMethodSettings = if (!ApplicationManager.getApplication.isUnitTestMode) {

      val dialog = new ScalaExtractMethodDialog(project, elements, hasReturn, lastReturn, sibling, scope,
        input.toArray, output.toArray)
      dialog.show
      if (!dialog.isOK) {
        return
      }
      dialog.getSettings
    } else {
      new ScalaExtractMethodSettings("testMethodName", ScalaExtractMethodUtils.getParameters(input.toArray, elements),
        ScalaExtractMethodUtils.getReturns(output.toArray, elements), "", scope, sibling,
        elements, hasReturn, lastReturn)
    }
    performRefactoring(settings, editor)
  }

  private def getTextForElement(element: PsiElement): String = {
    element.getParent match {
      case _: ScTemplateBody => "Extract class method"
      case _: ScBlock => "Extract local method"
      case _: ScalaFile => "Extract file method"
      case _ => "Unknown extraction"
    }
  }

  private def toStop {
    "stop"
  }

  private def performRefactoring(settings: ScalaExtractMethodSettings, editor: Editor) {


    val method = ScalaExtractMethodUtils.createMethodFromSettings(settings)
    val element = settings.elements.apply(0)
    val processor = new CompletionProcessor(StdKinds.refExprLastRef)
    PsiTreeUtil.treeWalkUp(processor, element, null, ResolveState.initial)
    val allNames = new HashSet[String]()
    allNames ++= processor.candidates.map(rr => rr.element.getName)
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
    val runnable = new Runnable {
      def run: Unit = {
        toStop
        PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)
        val scope = settings.scope
        val sibling = settings.nextSibling
        sibling.getParent.getNode.addChild(method.getNode, sibling.getNode)
        sibling.getParent.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(method.getManager), sibling.getNode)
        val methodCall = new StringBuilder(settings.methodName)
        if (settings.parameters.find(p => p.passAsParameter) != None) {
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
          settings.elements.apply(0).replace(getMatchForReturn(methodCall.toString))
        } else if (settings.returns.length == 1) {
          val ret = settings.returns.apply(0)
          val exprText = if (settings.returnType == None) methodCall.toString else tFreshName + "._2.get"
          val stmt: PsiElement = if (ret.needNewDefinition) {
            ScalaPsiElementFactory.createDeclaration(ret.returnType, ret.oldParamName, !ret.isVal,
              exprText, method.getManager, true)
          } else {
            ScalaPsiElementFactory.createExpressionFromText(ret.oldParamName + " = " +
                    exprText, settings.elements.apply(0).getManager)
          }
          val before = settings.elements.apply(0)
          def addNode(elem: PsiElement) {before.getParent.getNode.addChild(elem.getNode, before.getNode)}
          if (settings.returnType != None) {
            val decl = ScalaPsiElementFactory.createDeclaration(null, tFreshName, false, methodCall.toString, method.getManager, true)
            val nl1 = ScalaPsiElementFactory.createNewLineNode(decl.getManager).getPsi
            val nl2 = ScalaPsiElementFactory.createNewLineNode(decl.getManager).getPsi
            val matcher = getMatchForReturn(tFreshName + "._1")
            addNode(decl); addNode(nl2); addNode(matcher); addNode(nl1);
          }
          addNode(stmt)
          before.getParent.getNode.removeChild(before.getNode)
        } else {
          val exprText = if (settings.returnType == None) methodCall.toString else tFreshName + "._2.get"
          val stmt = ScalaPsiElementFactory.createDeclaration(null, rFreshName, false, exprText, method.getManager, true)
          val before = settings.elements.apply(0)
          def addNodeBack(elem: PsiElement) {before.getParent.getNode.addChild(elem.getNode, before.getNode)}
          if (settings.returnType != None) {
            val decl = ScalaPsiElementFactory.createDeclaration(null, tFreshName, false, methodCall.toString, method.getManager, true)
            val nl1 = ScalaPsiElementFactory.createNewLineNode(decl.getManager).getPsi
            val nl2 = ScalaPsiElementFactory.createNewLineNode(decl.getManager).getPsi
            val matcher = getMatchForReturn(tFreshName + "._1")
            addNodeBack(decl); addNodeBack(nl2); addNodeBack(matcher); addNodeBack(nl1);
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
                !ret.isVal, rFreshName + "._" + count, lastElem.getManager, true))
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
      }
    }
    CommandProcessor.getInstance.executeCommand(editor.getProject, new Runnable {
      def run: Unit = {
        ApplicationManager.getApplication.runWriteAction(runnable)
        editor.getSelectionModel.removeSelection
      }
    }, REFACTORING_NAME, null)
  }

  private def showErrorMessage(text: String, project: Project): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    val dialog = new RefactoringMessageDialog(REFACTORING_NAME, text,
            HelpID.EXTRACT_METHOD, "OptionPane.errorIcon", false, project)
    dialog.show
  }
}