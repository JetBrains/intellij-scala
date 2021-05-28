package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.formatting.IndentInfo
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.{Document, Editor, EditorModificationUtil, EditorModificationUtilEx}
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.editor.enterHandler.Scala3IndentationBasedSyntaxEnterHandler._
import org.jetbrains.plugins.scala.editor.{AutoBraceUtils, DocumentExt, PsiWhiteSpaceOps, ScalaEditorUtils, useIndentationBasedSyntax}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScCommentOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.util.IndentUtil

/**
 * Some other indentation-related logic:
 *  - [[com.intellij.codeInsight.editorActions.EnterHandler#executeWriteActionInner]]
 *  - [[com.intellij.formatting.FormatProcessor#getIndent]]
 *  - [[org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.isIncomplete]]
 *  - [[org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.getChildAttributes]]<br>
 *    used when typing after incomplete block, in the beginning of some structure, e.g.: {{{
 *      def foo = <caret>
 *    }}}
 */
class Scala3IndentationBasedSyntaxEnterHandler extends EnterHandlerDelegateAdapter {

  // NOTE: maybe we could move some logic here? investigate whether it has any advantages
  override def invokeInsideIndent(newLineCharOffset: Int, editor: Editor, dataContext: DataContext): Boolean =
    super.invokeInsideIndent(newLineCharOffset, editor, dataContext)

  override def preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffsetRef: Ref[Integer],
    caretAdvance: Ref[Integer],
    dataContext: DataContext,
    originalHandler: EditorActionHandler
  ): Result = {
    if (!file.is[ScalaFile])
      return Result.Continue

    if (!useIndentationBasedSyntax(file))
      return Result.Continue

    if (!CodeInsightSettings.getInstance.SMART_INDENT_ON_ENTER)
      return Result.Continue

    val caretOffset = caretOffsetRef.get.intValue

    val document = editor.getDocument

    val caretIsAtTheEndOfLine = isCaretAtTheEndOfLine(caretOffset, document)
    val result = if (caretIsAtTheEndOfLine) {
      // from [[com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.preprocessEnter]]:
      // Important Note: A document associated with the editor may have modifications which are not reflected yet in the PSI file.
      // If any s with PSI are needed including a search for PSI elements, the document must be committed first to update the PSI.
      document.commit(editor.getProject)

      val elementAtCaret = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, document, caretOffset)
      if (elementAtCaret == null)
        return Result.Continue

      val indentOptions = CodeStyle.getIndentOptions(file)
      checkCaretAfterEmptyCaseClauseArrow(elementAtCaret) match {
        case Some(clause) =>
          // WORKAROUND:
          // press Enter after the case clause WITHOUT any code in the body
          // `case _ =><caret>` (with potential spaces around caret)
          insertNewLineWithSpacesAtCaret(editor, document, clause, indentOptions, extraSpaces = CodeStyle.getIndentSize(file), needRemoveTrailingSpaces = true)
          Result.Stop
        case _ =>
          val indentedElementOpt = previousElementInIndentationContext(elementAtCaret, caretOffset, document.getCharsSequence, indentOptions)
          indentedElementOpt match {
            case Some((indentedElement, _)) =>
              insertNewLineWithSpacesAtCaret(editor, document, indentedElement, indentOptions, needRemoveTrailingSpaces = true)
              Result.Stop
            case _ =>
              Result.Continue
          }
      }
    }
    else {
      // looks like document commit is not required in this particular case
      val elementAtCaret = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, document, caretOffset)
      indentCodeAfterCaseClauseArrow(document, elementAtCaret, caretOffset)
      Result.Continue
    }
    //println(s"preprocessEnter: $result")
    result
  }
}

object Scala3IndentationBasedSyntaxEnterHandler {

  /**
   * The method returns '''Some(element)''' - if the element before the caret is in indentation context
   *
   * The method returns  '''None''' - otherwise, or for incomplete blocks
   * Examples of incomplete blocks:<br> {{{
   *   def foo = <caret>
   *   Option(42) match <caret>
   * }}}
   *
   *
   * Examples:
   * {{{
   *   // returns `statement2`
   *   def foo =
   *     statement1
   *     statement2<caret>
   *
   *   // returns `println(2)`
   *   class A {
   *     def foo = {
   *       if 2 + 2 == 42 then
   *         println(1)
   *         println(2)  <caret>
   *     }
   * }}}
   *
   * If the caret is at a blank line, then it's indent is checked with the "indented element" candidate.
   * If the caret is indented more or equal than the previous element, then it's meant that the caret is inside
   * same indentation context. If the caret is indented less, then the element is skipped.<br>
   *
   * Examples (using 4-space indent for a better visibility)
   * {{{
   *   // returns `println(2)`
   *   class A:
   *       def foo =
   *           println("start")
   *           if 2 + 2 == 42 then
   *               println(1)
   *               println(2)
   *                   <caret>
   *
   *   // returns `if ...`
   *   class A:
   *       def foo =
   *           println("start")
   *           if 2 + 2 == 42 then
   *               println(1)
   *               println(2)
   *             <caret>
   *
   *   // returns `def foo ...`
   *   class A:
   *       def foo =
   *           println("start")
   *           if 2 + 2 == 42 then
   *               println(1)
   *               println(2)
   *         <caret>
   * }}}
   *
   *
   * @todo extract to [[AutoBraceUtils]] if want to reuse e.g. in backspace handlers?
   */
    private[editor] def previousElementInIndentationContext(
    @NotNull elementAtCaret: PsiElement,
    caretOffset: Int,
    documentText: CharSequence,
    indentOptions: IndentOptions
  ): Option[(PsiElement, Int)] = {
    // NOTE 1: there are still some issues with Scala3 + tabs, see: SCL-18817
    // NOTE 2: compiler indent calculation in Scala3 is a bit different from ours,
    //  according to http://dotty.epfl.ch/docs/reference/other-new-features/indentation.html
    //  "Indentation prefixes can consist of spaces and/or tabs. Indentation widths are the indentation prefixes themselves"

    val caretIndent = EnterHandlerUtils.calcCaretIndent(caretOffset, documentText, indentOptions.TAB_SIZE)
    val caretIndentSize = caretIndent.getOrElse(Int.MaxValue) // using MaxValue if the caret ins not inside code indent

    val lastRealElement = elementAtCaret match {
      case ws: PsiWhiteSpace => PsiTreeUtil.prevLeaf(ws) match {
        case null => ws
        case prev => prev
      }
      case el => el
    }
    assert(lastRealElement != null)
    val result = if (lastRealElement.is[PsiErrorElement])
      None
    else {
      val realElementOffset = lastRealElement.endOffset

      val withParents0 = lastRealElement.withParentsInFile: Iterator[PsiElement]
      val withParents1 = withParents0.takeWhile(e => e != null && e.endOffset <= realElementOffset)
      val indented = withParents1.flatMap(parent => toIndentedElement(parent, caretIndentSize, indentOptions))
      indented.headOption
    }
    //println(s"indentedElement: $result")
    result
  }

  private def toIndentedElement(
    element: PsiElement,
    caretIndentSize: Int,
    indentOptions: IndentOptions
  ): Option[(PsiElement, Int)] = {
    if (isElementIsInIndentationContext(element))
      for {
        elementIndentSize <- elementIndentSize(element, maxElementIndentSize = caretIndentSize, indentOptions.TAB_SIZE)
      } yield {
        (element, elementIndentSize)
      }
    else None
  }

  private def isElementIsInIndentationContext(element: PsiElement): Boolean = {
    // TODO: it should be just ScBlockStatement, without ScCommentOwner:
    //  according to the language spec, definitions are also block statements,
    //  but in our hierarchy they are not, we should try adding ScBlockStatement to all Definition PSI hierarchy
    element match {
      // An indentation region can start after one of the following tokens:
      // =  =>  ?=>  <-  catch  do  else  finally  for
      // if  match  return  then  throw  try  while  yield
      case _: ScBlockStatement | _: ScCommentOwner =>
        val parent = element.getParent


        val isInsideTemplateBody = parent.is[ScTemplateBody]

        val isInsideIndentationBlock = parent match {
          case block: ScBlock =>
            !block.isEnclosedByBraces
          case _              => false
        }

        // This check is actual when body consists from a single element.
        // In this case parser doesn't wrap it into a ScBodyExpr PSI element
        val isInsideIndentationBlock_AsSingleBlockElement1 = {
          val prevCodeLeaf = PsiTreeUtil.prevCodeLeaf(element)
          prevCodeLeaf != null && (prevCodeLeaf.elementType match {
            case ScalaTokenTypes.tASSIGN |
                 ScalaTokenTypes.tFUNTYPE |
                 ScalaTokenType.ImplicitFunctionArrow |
                 ScalaTokenTypes.tCHOOSE |

                 ScalaTokenTypes.kYIELD |
                 ScalaTokenTypes.kDO |
                 ScalaTokenType.ThenKeyword |
                 ScalaTokenTypes.kELSE |
                 ScalaTokenTypes.kTRY |
                 ScalaTokenTypes.kFINALLY |

                 // NOTE: these expressions are handled specially, using some PSI extractors, because
                 //  for them, it's not enough to check just previous token
                 //ScalaTokenTypes.kIF |
                 //ScalaTokenTypes.kFOR |
                 //ScalaTokenTypes.kWHILE |

                 ScalaTokenTypes.kCATCH |
                 //ScalaTokenTypes.kMATCH // case clauses are handled specially

                 ScalaTokenTypes.kRETURN |
                 ScalaTokenTypes.kTHROW =>
              true
            case _                      =>
              false
          })
        }

        val isInsideIndentationBlock_AsSingleBlockElement2 = parent match {
          case ScIf(_, thenBranch, elseBranch) => thenBranch.contains(element) || elseBranch.contains(element)
          case ScWhile(_, Some(`element`))     => true // TODO: use just expression extractor (condition is ignored, but calculated redundantly)
          case ScFor(_, `element`)             => true // TODO: use just body extractor (same reason)
          case _                               => false
        }

        val isInIndentationContext =
          isInsideTemplateBody ||
            isInsideIndentationBlock ||
            isInsideIndentationBlock_AsSingleBlockElement1 ||
            isInsideIndentationBlock_AsSingleBlockElement2

        isInIndentationContext

      case clause: ScCaseClause =>
        // WORKAROUND:
        // press Enter / Backspace after the LAST case clause WITH some code on the same line with clause arrow
        // `case _ => doSomething()<caret>`
        // NOTE: in case clauses with braces, this automatically works via `ScalaBlock.getChildAttributes`.
        // However with braceless clauses selected formatter block belongs to the parent scope, not to the clauses
        // so wrong indent is used without this workaround
        val isLastClause = clause.getNextSibling == null
        val isBracelessClauses: Boolean = {
          val clauses = clause.getParent
          val prev = PsiTreeUtil.prevCodeLeaf(clauses)
          prev != null && prev.elementType != ScalaTokenTypes.tLBRACE
        }
        isLastClause && isBracelessClauses
      case _ =>
         false
    }
  }

  private def elementIndentSize(element: PsiElement, maxElementIndentSize: Int, tabSize: Int): Option[Int] = {
    val indentWs = EnterHandlerUtils.precededIndentWhitespace(element)
    indentWs match {
      case Some(ws) =>
        val elementIndentSize = IndentUtil.calcLastLineIndent(ws.getChars, tabSize)

        /** see docs and examples in [[previousElementInIndentationContext]] */
        if (elementIndentSize <= maxElementIndentSize) {
          // Incomplete elements are handled in the end of enter handling by IntelliJ when adjusting indents.
          // see: com.intellij.codeInsight.editorActions.EnterHandler.executeWriteActionInner
          // todo (optimization): we could try to stop processing parents of the original elementAtCaret,
          //  once we detect some incomplete parent
          //  (currently we continue processing parents and do this check each time
          if (!ScalaBlock.isIncomplete(element.getNode))
            Some(elementIndentSize)
          else None
        }
        else None
      case _ => None
    }
  }

  /**
   * When the caret is just after case clause arrow `=>` and just before some code position {{{
   *   expr match
   *     case 42 => <caret>println("hello")
   * }}}
   * we need to insert an extra space before the code.
   * Otherwise Scala 3 parser will not parse the code as a child of the cause clause and it will not be indented:
   * {{{
   * BAD:
   * 1 match
   * case 2 =>
   * <CARET>3
   *
   * GOOD:
   * 1 match
   * case 2 =>
   * <CARET> 3
   * }}}
   */
  private def indentCodeAfterCaseClauseArrow(
    document: Document,
    elementAtCaret: PsiElement,
    caretOffset: Int
  ): Unit =
    if (isCaretAfterCaseClauseArrowBeforeCode(elementAtCaret, caretOffset)) {
      document.insertString(caretOffset, " ")
    }

  // `case _ =>   <caret>ref`
  private def isCaretAfterCaseClauseArrowBeforeCode(elementAtCaret: PsiElement, caretOffset: Int): Boolean = {
    val prevLeaf = PsiTreeUtil.prevCodeLeaf(elementAtCaret)
    prevLeaf match {
      case ElementType(ScalaTokenTypes.tFUNTYPE) && Parent(_: ScCaseClause) if caretOffset == elementAtCaret.startOffset =>
        true
      case _                                                                                                             =>
        false
    }
  }

  /** @return Some(caseClause) if element before the caret represents a
   *         case clause without any code after the caret:
   *         {{{case _ =><caret><new line> (with optional spaces around caret)}}}
   */
  private def checkCaretAfterEmptyCaseClauseArrow(elementAtCaret: PsiElement): Option[ScCaseClause] = {
    val canBeAfterCaseClauseArrow =
      elementAtCaret match {
        // `case _ =><caret>EOF` (no whitespaces around caret, caret is at the end of file)
        // in this case element at caret represents empty case clause body
        case block: ScBlock   => block.getFirstChild == null
        case _: PsiWhiteSpace => true
        case _                => false
      }
    if (canBeAfterCaseClauseArrow) {
      val prevLeaf = PsiTreeUtil.prevCodeLeaf(elementAtCaret)
      prevLeaf match {
        case ElementType(ScalaTokenTypes.tFUNTYPE) && Parent(clause: ScCaseClause) =>
          Some(clause)
        case _ => None
      }
    }
    else None
  }

  private val SpaceOrTab = " \t"



  /** The logic is inspired by [[com.intellij.openapi.editor.actions.EnterAction.insertNewLineAtCaret]] */
  private def insertNewLineWithSpacesAtCaret(
    editor: Editor,
    document: Document,
    indentedElement: PsiElement,
    indentOptions: IndentOptions,
    needRemoveTrailingSpaces: Boolean = false,
    extraSpaces: Int = 0
  ): Unit = {
    val text = document.getCharsSequence
    val caretOffset = editor.getCaretModel.getOffset

    val prevIndentLineStartOffset = DocumentUtil.getLineStartOffset(indentedElement.startOffset, document)
    val prevIndentWsEndOffset = CharArrayUtil.shiftForward(text, prevIndentLineStartOffset, SpaceOrTab)
    // in case caret is placed before some element inside whitespace:
    // def foo =
    //    <caret>  42
    val prevIndentWsEndOffsetUntilCaret = prevIndentWsEndOffset.min(caretOffset)

    val spacesOnNewLine = text.subSequence(prevIndentLineStartOffset, prevIndentWsEndOffsetUntilCaret)
    val indentSize = IndentUtil.calcIndent(spacesOnNewLine, indentOptions.TAB_SIZE) + extraSpaces
    val indentString = new IndentInfo(1, indentSize, 0).generateNewWhiteSpace(indentOptions)

    document.insertString(caretOffset, indentString)
    val newCaretOffset = caretOffset + indentString.length
    editor.getCaretModel.moveToOffset(newCaretOffset)
    EditorModificationUtilEx.scrollToCaret(editor)
    editor.getSelectionModel.removeSelection()

    if (needRemoveTrailingSpaces) {
      removeTrailingSpaces(document, newCaretOffset)
    }
  }

  private def removeTrailingSpaces(document: Document, startOffset: Int): Unit = {
    val documentText = document.getCharsSequence

    val endOffset = CharArrayUtil.shiftForward(documentText, startOffset, SpaceOrTab)

    if (endOffset == documentText.length() || documentText.charAt(endOffset) == '\n') {
      document.deleteString(startOffset, endOffset)
    }
  }

  private def isCaretAtTheEndOfLine(caretOffset: Int, document: Document): Boolean = {
    val documentText = document.getCharsSequence
    val shifted = CharArrayUtil.shiftForward(documentText, caretOffset, SpaceOrTab)
    shifted == documentText.length || documentText.charAt(shifted) == '\n'
  }
}
