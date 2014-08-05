package org.jetbrains.plugins.scala
package editor.smartEnter

import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.{Document, RangeMarker, Editor}
import com.intellij.psi._
import fixers._
import java.util
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.util.IncorrectOperationException
import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.util.Key
import com.intellij.featureStatistics.FeatureUsageTracker
import java.lang.String
import java.lang.Long
import com.intellij.util.text.CharArrayUtil
import com.intellij.psi.codeStyle.{CodeStyleSettingsManager, CodeStyleSettings}
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.expr._
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.actionSystem.{EditorActionManager, EditorActionHandler}
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.codeInsight.editorActions.smartEnter._
import lang.psi.api.statements.ScPatternDefinition

/**
 * @author Ksenia.Sautina
 * @since 1/28/13
 */

object ScalaSmartEnterProcessor {
  private val LOG = Logger.getInstance(getClass)
  private final var ourEnterProcessors: Array[EnterProcessor] = null

  val fixers = new util.ArrayList[ScalaFixer]
  fixers.add(new ScalaMethodCallFixer)
  fixers.add(new ScalaIfConditionFixer)
  fixers.add(new ScalaForStatementFixer)
  fixers.add(new ScalaWhileConditionFixer)
  fixers.add(new ScalaMissingWhileBodyFixer)
  fixers.add(new ScalaMissingIfBranchesFixer)
  fixers.add(new ScalaMissingForBodyFixer)

  val processors = new util.ArrayList[EnterProcessor]
  ourEnterProcessors = processors.toArray(new Array[EnterProcessor](processors.size))
}

class ScalaSmartEnterProcessor extends SmartEnterProcessor {
  private var myFirstErrorOffset: Int = Integer.MAX_VALUE
  private var mySkipEnter: Boolean = false
  private final val MAX_ATTEMPTS: Int = 20
  private final val SMART_ENTER_TIMESTAMP: Key[Long]  = Key.create("smartEnterOriginalTimestamp")

  class TooManyAttemptsException extends Exception {
  }

  def process(project: Project, editor: Editor, psiFile: PsiFile) = {
    FeatureUsageTracker.getInstance.triggerFeatureUsed("codeassists.complete.statement")

    val document: Document = editor.getDocument
    val textForRollback: String = document.getText

    try {
      editor.putUserData(SMART_ENTER_TIMESTAMP, editor.getDocument.getModificationStamp.asInstanceOf[Long])
      myFirstErrorOffset = Integer.MAX_VALUE
      mySkipEnter = false
      process(project, editor, psiFile, 0)
    } catch {
      case e: TooManyAttemptsException =>
        document.replaceString(0, document.getTextLength, textForRollback)
    }
    finally {
      editor.putUserData(SMART_ENTER_TIMESTAMP, null)
    }
    true
  }

  private def process(project: Project, editor: Editor, file: PsiFile, attempt: Int) {
    if (attempt > MAX_ATTEMPTS) throw new TooManyAttemptsException

    try {
      commit(editor)
      if (myFirstErrorOffset != Integer.MAX_VALUE) {
        editor.getCaretModel.moveToOffset(myFirstErrorOffset)
      }

      myFirstErrorOffset = Integer.MAX_VALUE

      val atCaret: PsiElement = getStatementAtCaret(editor, file)
      if (atCaret == null) {
        return
      }

      val queue = new util.ArrayList[PsiElement]
      collectAllElements(atCaret, queue, rec = true)
      queue.add(atCaret)

      import scala.collection.JavaConversions._
      for (psiElement <- queue) {
        for (fixer <- ScalaSmartEnterProcessor.fixers) {
          fixer.apply(editor, this, psiElement)
          if (LookupManager.getInstance(project).getActiveLookup != null) {
            return
          }
          if (isUncommited(project) || !psiElement.isValid) {
            moveCaretInsideBracesIfAny(editor, file)
            process(project, editor, file, attempt + 1)
            return
          }
        }
      }
      doEnter(atCaret, editor)
    }
    catch {
      case e: IncorrectOperationException => {
        ScalaSmartEnterProcessor.LOG.error(e.getMessage)
      }
    }
  }

  protected override def reformat(caret: PsiElement) {
    if (caret == null) {
      return
    }
    var atCaret = caret
    val parent: PsiElement = atCaret.getParent
    parent match {
      case block: ScBlockExpr => {
        if (block.exprs.length > 0 && block.exprs.apply(0) == atCaret) {
          atCaret = block
        }
      }
      case forStmt: ScForStatement => atCaret = forStmt
      case _ =>
    }

    super.reformat(atCaret)
  }

  private def doEnter(caret: PsiElement, editor: Editor) {
    var atCaret = caret
    val psiFile: PsiFile = atCaret.getContainingFile
    val rangeMarker: RangeMarker = createRangeMarker(atCaret)
    if (myFirstErrorOffset != Integer.MAX_VALUE) {
      editor.getCaretModel.moveToOffset(myFirstErrorOffset)
      reformat(atCaret)
      return
    }
    reformat(atCaret)
    commit(editor)
    if (mySkipEnter) {
      return
    }
    atCaret = CodeInsightUtil.findElementInRange(psiFile, rangeMarker.getStartOffset, rangeMarker.getEndOffset, atCaret.getClass)
    for (processor <- ScalaSmartEnterProcessor.ourEnterProcessors) {
      if (atCaret != null && processor.doEnter(editor, atCaret, isModified(editor))) return
    }
    if (!isModified(editor)) {
      plainEnter(editor)
    }
    else {
      if (myFirstErrorOffset == Integer.MAX_VALUE) {
        editor.getCaretModel.moveToOffset(rangeMarker.getEndOffset)
      }
      else {
        editor.getCaretModel.moveToOffset(myFirstErrorOffset)
      }
    }
  }

  private def collectAllElements(atCaret: PsiElement, res: util.ArrayList[PsiElement], rec: Boolean) {
    res.add(0, atCaret)
    var recourse = rec
    if (doNotStepInto(atCaret)) {
      if (!recourse) return
      recourse = false
    }
    val children: Array[PsiElement] = atCaret.getChildren
    for (child <- children) {
      collectAllElements(child, res, recourse)
    }
  }

  private def doNotStepInto(element: PsiElement): Boolean = {
    element.isInstanceOf[PsiClass] || element.isInstanceOf[PsiStatement] || element.isInstanceOf[PsiMethod]
  }

  protected override def getStatementAtCaret(editor: Editor, psiFile: PsiFile): PsiElement = {
    val atCaret: PsiElement = super.getStatementAtCaret(editor, psiFile)
    if (atCaret.isInstanceOf[PsiWhiteSpace] || atCaret == null) return null
    if (("}" == atCaret.getText) && !atCaret.getParent.isInstanceOf[PsiArrayInitializerExpression]) return null
    var statementAtCaret: PsiElement = PsiTreeUtil.getParentOfType(atCaret, classOf[ScPatternDefinition], classOf[ScIfStmt], classOf[ScWhileStmt], classOf[ScForStatement], classOf[ScCatchBlock], classOf[ScMethodCall])
    if (statementAtCaret.isInstanceOf[PsiBlockStatement]) return null
    if (statementAtCaret != null && statementAtCaret.getParent.isInstanceOf[ScForStatement]) {
      if (!PsiTreeUtil.hasErrorElements(statementAtCaret)) {
        statementAtCaret = statementAtCaret.getParent
      }
    }
    statementAtCaret match {
      case _: ScPatternDefinition | _: ScIfStmt | _: ScWhileStmt | _: ScForStatement | _: ScCatchBlock | _: ScMethodCall => statementAtCaret
      case _ => null
    }
  }

  protected def moveCaretInsideBracesIfAny(editor: Editor, file: PsiFile) {
    var caretOffset: Int = editor.getCaretModel.getOffset
    val chars: CharSequence = editor.getDocument.getCharsSequence
    if (CharArrayUtil.regionMatches(chars, caretOffset, "{}")) {
      caretOffset += 2
    } else if (CharArrayUtil.regionMatches(chars, caretOffset, "{\n}")) {
      caretOffset += 3
    } else if (CharArrayUtil.regionMatches(chars, caretOffset, "{\n\n}")) {
      caretOffset += 3
    }
    caretOffset = CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t") + 1
    if (CharArrayUtil.regionMatches(chars, caretOffset - "{}".length, "{}") || CharArrayUtil.regionMatches(chars, caretOffset - "{\n}".length, "{\n}")) {
      commit(editor)
      val settings: CodeStyleSettings = CodeStyleSettingsManager.getSettings(file.getProject)
      val old: Boolean = settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
      val elt: PsiElement = PsiTreeUtil.getParentOfType(file.findElementAt(caretOffset - 1), classOf[ScBlock])
      reformat(elt)
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = old
      editor.getCaretModel.moveToOffset(caretOffset - 1)
    }
  }

  def registerUnresolvedError(offset: Int) {
    if (myFirstErrorOffset > offset) {
      myFirstErrorOffset = offset
    }
  }

  def setSkipEnter(skipEnter: Boolean) {
    mySkipEnter = skipEnter
  }

  protected def isUncommited(project: Project): Boolean = {
    PsiDocumentManager.getInstance(project).hasUncommitedDocuments
  }

  protected def plainEnter(editor: Editor) {
    getEnterHandler.execute(editor, editor.asInstanceOf[EditorEx].getDataContext)
  }

  protected def getEnterHandler: EditorActionHandler = {
    EditorActionManager.getInstance.getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE)
  }

  protected def isModified(editor: Editor): Boolean = {
    val timestamp: Long = editor.getUserData(SMART_ENTER_TIMESTAMP)
    editor.getDocument.getModificationStamp != timestamp.longValue
  }
}
