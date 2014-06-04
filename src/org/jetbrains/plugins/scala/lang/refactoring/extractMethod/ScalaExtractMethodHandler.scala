package org.jetbrains.plugins.scala
package lang
package refactoring.extractMethod

import com.intellij.psi._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.types.ScType
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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScPatternDefinition, ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaRecursiveElementVisitor, ScalaFile}
import psi.api.base.patterns.ScCaseClause
import psi.types.result.TypingContext
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.psi.codeStyle.CodeStyleManager
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.LocalSearchScope
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates.DuplicatesUtil
import org.jetbrains.plugins.scala.lang.rearranger.ScalaRearranger
import com.intellij.internal.statistic.UsageTrigger

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

  private def invokeOnEditor(project: Project, editor: Editor, file: ScalaFile, dataContext: DataContext) {
    if (!ScalaRefactoringUtil.ensureFileWritable(project, file)) {
      showErrorMessage(ScalaBundle.message("file.is.not.writable"), project, editor)
      return
    }
    if (!editor.getSelectionModel.hasSelection) return
    ScalaRefactoringUtil.trimSpacesAndComments(editor, file, trimComments = false)
    val startElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionStart)
    val endElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionEnd - 1)
    val elements = ScalaPsiUtil.getElementsRange(startElement, endElement) match {
      case Seq(b: ScBlock) if !b.hasRBrace => b.children.toSeq
      case elems => elems
    }

    if (showNotPossibleWarnings(elements, project, editor)) return

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

    def returnType: Option[ScType] = {
      val fun = PsiTreeUtil.getParentOfType(elements(0), classOf[ScFunctionDefinition])
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
      for (element <- elements if result == None) {
        element.accept(visitor)
      }
      result
    }

    val lastScalaElem = elements.reverse.find(_.isInstanceOf[ScalaPsiElement]).get
    val lastReturn = checkLastReturn(lastScalaElem)
    val isLastExpressionMeaningful: Option[ScType] = {
      if (lastReturn) None
      else if (checkLastExpressionMeaningful(lastScalaElem))
        Some(lastScalaElem.asInstanceOf[ScExpression].getType(TypingContext.empty).getOrAny)
      else None
    }
    val hasReturn: Option[ScType] = returnType
    val stopAtScope: PsiElement = findScopeBound(elements).getOrElse(file)
    val siblings: Array[PsiElement] = getSiblings(elements(0), stopAtScope)
    if (siblings.length == 0) return
    val array = elements.toArray
    if (ApplicationManager.getApplication.isUnitTestMode && siblings.length > 0) {
      invokeDialog(project, editor, array, hasReturn, lastReturn, siblings(0), siblings.length == 1,
        isLastExpressionMeaningful)
    } else if (siblings.length > 1) {
      ScalaRefactoringUtil.showChooser(editor, siblings, {selectedValue =>
        invokeDialog(project, editor, array, hasReturn, lastReturn, selectedValue,
          siblings(siblings.length - 1) == selectedValue, isLastExpressionMeaningful)
      }, "Choose level for Extract Method", getTextForElement, true)
      return
    }
    else if (siblings.length == 1) {
      invokeDialog(project, editor, array, hasReturn, lastReturn, siblings(0), smallestScope = true, isLastExpressionMeaningful)
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
          case ScalaPsiUtil.inNameContext(varDef: ScVariableDefinition) if ScalaPsiUtil.isLValue(ref) => varDef.parent
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
                           lastMeaningful: Option[ScType]) {

    val info = ReachingDefintionsCollector.collectVariableInfo(elements, sibling.asInstanceOf[ScalaPsiElement])

    val input = info.inputVariables
    val output = info.outputVariables
    if (output.exists(_.element.isInstanceOf[ScFunctionDefinition])) {
      showErrorMessage(ScalaBundle.message("cannot.extract.used.function.definition"), project, editor)
      return
    }
    val settings: ScalaExtractMethodSettings =
      if (!ApplicationManager.getApplication.isUnitTestMode) {
        val dialog = new ScalaExtractMethodDialog(project, elements, hasReturn, lastReturn, sibling,
          input.toArray, output.toArray, lastMeaningful)
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
          elements, hasReturn, lastReturn, lastMeaningful, innerClassSettings)
      }
    val duplicates = DuplicatesUtil.findDuplicates(settings)
    performRefactoring(settings, editor)
    if (settings.returnType.isEmpty && settings.typeParameters.isEmpty) {
      if (duplicates.size > 0) DuplicatesUtil.processDuplicates(duplicates, settings, project, editor)
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
    if (method == null) return
    val ics = settings.innerClassSettings

    def newLine = ScalaPsiElementFactory.createNewLine(method.getManager)

    def addElementBefore(elem: PsiElement, nextSibling: PsiElement) = {
      val added = nextSibling.getParent.addBefore(elem, nextSibling)
      ScalaPsiUtil.adjustTypes(added)
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

    def insertMethodCall() =
      ScalaExtractMethodUtils.replaceWithMethodCall(settings, settings.elements, param => param.oldName, output => output.paramName)


    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)

    inWriteCommandAction(editor.getProject, REFACTORING_NAME) {

      val method = insertMethod()
      insertInnerClassBefore(method)
      insertMethodCall()

      val manager = CodeStyleManager.getInstance(method.getProject)
      manager.reformat(method)
      editor.getSelectionModel.removeSelection()
    }
  }

  private def showNotPossibleWarnings(elements: Seq[PsiElement], project: Project, editor: Editor): Boolean = {
    def errors(elem: PsiElement): Option[String] = elem match {
      case _: ScBlockStatement => None
      case comm: PsiComment if !comm.getParent.isInstanceOf[ScMember] => None
      case _: PsiWhiteSpace => None
      case _ if ScalaTokenTypes.tSEMICOLON == elem.getNode.getElementType => None
      case typedef: ScTypeDefinition => checkTypeDefUsages(typedef)
      case _: ScSelfInvocation => ScalaBundle.message("cannot.extract.self.invocation").toOption
      case _ => ScalaBundle.message("cannot.extract.empty.message").toOption
    }

    def checkTypeDefUsages(typedef: ScTypeDefinition): Option[String] = {
      val scope = new LocalSearchScope(PsiTreeUtil.getParentOfType(typedef, classOf[ScControlFlowOwner], true))
      val refs = ReferencesSearch.search(typedef, scope).findAll()
      import scala.collection.JavaConverters.collectionAsScalaIterableConverter
      for {
        ref <- refs.asScala
        if !elements.exists(PsiTreeUtil.isAncestor(_, ref.getElement, false))
      } {
        return ScalaBundle.message("cannot.extract.used.type.definition").toOption
      }
      None
    }

    val messages = elements.flatMap(errors)
    if (messages.nonEmpty) {
      showErrorMessage(messages.mkString("\n"), project, editor)
      return true
    }

    if (elements.length == 0 || !elements.exists(_.isInstanceOf[ScBlockStatement])) {
      showErrorMessage(ScalaBundle.message("cannot.extract.empty.message"), project, editor)
      return true
    }

    var typeDefMessage: Option[String] = None
    for (element <- elements) {
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitTypeDefintion(typedef: ScTypeDefinition) {
          typeDefMessage = checkTypeDefUsages(typedef)
        }
      }
      element.accept(visitor)
      typeDefMessage match {
        case Some(m) =>
          showErrorMessage(m, project, editor)
          return true
        case None =>
      }
    }
    false
  }

  private def showErrorMessage(text: String, project: Project, editor: Editor) {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    CommonRefactoringUtil.showErrorHint(project, editor, text, REFACTORING_NAME, HelpID.EXTRACT_METHOD)
  }
}