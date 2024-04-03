package org.jetbrains.plugins.scala.editor

import com.intellij.psi._
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{ApiStatus, NotNull}
import org.jetbrains.plugins.scala.editor.enterHandler.Scala3IndentationBasedSyntaxEnterHandler._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScCommentOwner, ScEnumCases, ScExtensionBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.util.IndentUtil

/**
 * @note logic inside this utility class was originally located in [[org.jetbrains.plugins.scala.editor.enterHandler.Scala3IndentationBasedSyntaxEnterHandler]].
 *       Currently there are no direct tests for [[ScalaIndentationSyntaxUtils]],
 *       however it's tested indirectly via enter-handler & backspace-handler and other tests.<br>
 *       However, ideally, it would be nice to test functionality of this class more explicitly in dedicated test
 * @see [[org.jetbrains.plugins.scala.util.IndentUtil]]
 */
object ScalaIndentationSyntaxUtils {
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
   * @todo extract to [[AutoBraceUtils]] if want to reuse e.g. in backspace handlers?
   */
  @ApiStatus.Experimental
  def previousElementInIndentationContext(
    @NotNull elementAtCaret: PsiElement,
    caretIndentSize: Int,
    indentOptions: IndentOptions
  ): Option[(PsiElement, Int)] = {
    // NOTE 1: there are still some issues with Scala3 + tabs, see: SCL-18817
    // NOTE 2: compiler indent calculation in Scala3 is a bit different from ours,
    //  according to http://dotty.epfl.ch/docs/reference/other-new-features/indentation.html
    //  "Indentation prefixes can consist of spaces and/or tabs. Indentation widths are the indentation prefixes themselves"

    val lastRealElement = getLastRealElement(elementAtCaret)
    val result = if (lastRealElement == null)
      None
    else if (lastRealElement.is[PsiErrorElement])
      None
    else {
      val elementAtCaretEndOffset = elementAtCaret.endOffset

      var result: Option[(PsiElement, Int)] = None

      // For a given last real element on the line traverse the tree searching for an indented element
      // (yes, it's not very pretty, but the logic of tree traversal is not simple and it's easier to modify the imperative code)
      var current = lastRealElement
      var continue = true
      while (continue) {
        if (current != null && current.endOffset <= elementAtCaretEndOffset && !current.is[PsiFile]) {
          toIndentedElement(current, caretIndentSize, indentOptions) match {
            case Some(value) =>
              result = Some(value)
              continue = false

            case None =>
              val nextElementToProcess = {
                val prevCode = current.prevSiblingNotWhitespace.orNull
                val isInSemicolonSeparatedList =
                  current.elementType == ScalaTokenTypes.tSEMICOLON ||
                    prevCode != null && prevCode.elementType == ScalaTokenTypes.tSEMICOLON
                if (isInSemicolonSeparatedList)
                  prevCode
                else
                  current.getParent
              }
              current = nextElementToProcess
          }
        }
        else {
          continue = false
        }
      }
      result
    }
    //println(s"indentedElement: $result")
    result
  }

  private def toIndentedElement(
    element: PsiElement,
    caretIndentSize: Int,
    indentOptions: IndentOptions
  ): Option[(PsiElement, Int)] = {
    if (isElementInIndentationContext(element))
      for {
        elementIndentSize <- elementIndentSize(element, maxElementIndentSize = caretIndentSize, indentOptions.TAB_SIZE)
      } yield {
        (element, elementIndentSize)
      }
    else None
  }

  private def isElementInIndentationContext(element: PsiElement): Boolean = {
    // TODO: it should be just ScBlockStatement, without ScCommentOwner:
    //  according to the language spec, definitions are also block statements,
    //  but in our hierarchy they are not, we should try adding ScBlockStatement to all Definition PSI hierarchy
    val isBlockChild = element.is[ScBlockStatement, ScExportStmt, ScPackaging] ||
      element.isInstanceOf[ScCommentOwner] ||
      element.elementType == ScalaTokenTypes.tSEMICOLON
    element match {
      // An indentation region can start after one of the following tokens:
      // =  =>  ?=>  <-  catch  do  else  finally  for
      // if  match  return  then  throw  try  while  yield
      case _ if isBlockChild =>
        val parent = element.getParent

        val isInsideIndentationBlock = parent match {
          case block: ScBlock =>
            !block.isEnclosedByBraces
          case _ => false
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

                 // NOTE: these expressions are handled specially, using some PSI extractors,
                 //  For them  not enough to just check the previous token: previous token can be ')'
                 //  or some element of condition / enumerator
                 //
                 //ScalaTokenTypes.kIF |
                 //ScalaTokenTypes.kFOR |
                 //ScalaTokenTypes.kWHILE |

                 ScalaTokenTypes.kCATCH |
                 //ScalaTokenTypes.kMATCH // case clauses are handled specially

                 ScalaTokenTypes.kRETURN |
                 ScalaTokenTypes.kTHROW =>
              true
            case _ =>
              false
          })
        }

        val isInsideIndentationBlock_AsSingleBlockElement2 = parent match {
          case ScIf(_, thenBranch, elseBranch) => thenBranch.contains(element) || elseBranch.contains(element)
          case ScWhile(_, Some(`element`)) => true // TODO: use just expression extractor (condition is ignored, but calculated redundantly)
          case ScFor(_, `element`) => true // TODO: use just body extractor (same reason)
          case _ => false
        }

        val isInIndentationContext =
          parent.is[ScTemplateBody] ||
            parent.is[ScExtensionBody] ||
            parent.is[ScPackaging] ||
            isInsideIndentationBlock ||
            isInsideIndentationBlock_AsSingleBlockElement1 ||
            isInsideIndentationBlock_AsSingleBlockElement2

        isInIndentationContext

      case clause: ScCaseClause =>

        /**
         * WORKAROUND:
         * press Enter / Backspace after the LAST case clause WITH some code on the same line with clause arrow
         * before: {{{
         *   ref match
         *     case _ => doSomething()<caret>
         * }}}
         *
         * after: {{{
         *   ref match
         *     case _ => doSomething()
         *     <caret>
         * }}}
         *
         * NOTE: in case clauses with braces, this automatically works via `ScalaBlock.getChildAttributes`.
         * However with braceless clauses selected formatter block belongs to the parent scope, not to the clauses
         * so wrong indent is used without this workaround
         */
        val isLastClause = clause.getNextSibling == null
        val isInIndentationSyntax: Boolean = {
          val clauses = clause.getParent
          val prev = PsiTreeUtil.prevCodeLeaf(clauses)
          prev != null && prev.elementType != ScalaTokenTypes.tLBRACE
        }
        isLastClause && isInIndentationSyntax
      case _: PsiComment =>
        true
      case _: ScEnumCases =>
        true
      case _ =>
        false
    }
  }

  def elementIndentSize(element: PsiElement, maxElementIndentSize: Int, tabSize: Int): Option[Int] = {
    val indentWs = precededIndentWhitespace(element)
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

  private def precededIndentWhitespace(element: PsiElement): Option[PsiWhiteSpace] =
    element.getPrevNonEmptyLeaf match {
      case ws: PsiWhiteSpace if ws.textContains('\n') => Some(ws)
      case _ => None
    }
}
