package org.jetbrains.plugins.hocon.editor

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover.MoveInfo
import com.intellij.codeInsight.editorActions.moveUpDown.{LineMover, LineRange}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.hocon.editor.HoconObjectEntryMover.{PrefixModKey, PrefixModification}
import org.jetbrains.plugins.hocon.psi._

import scala.annotation.tailrec

/**
  * An implementation of [[com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover]] which can
  * move entire HOCON object entries (object fields or include statements).
  *
  * The entry being moved is being required to be the only entry in its own lines, i.e. there may not be any other
  * entry (or left/right object brace) in the first or last line occupied by the entry being moved. If this requirement is not
  * met, the "move statement" action will fallback to "move line".
  * <p/>
  * If the entry is "movable" (as defined above), the four scenarios are possible:
  * <p/>
  * 1. An object field may be "taken out" of its enclosing object field and have its prefix prepended, e.g.
  * {{{
  *   a {
  *     |b = c
  *   }
  * }}}
  * After "move statement up":
  * {{{
  *   a.|b = c
  *   a {
  *   }
  * }}}
  * <p/>
  * 2. An object field may be "inserted" into adjacent object field and have its prefix removed, i.e. a reverse
  * operation to "taking out". This is only possible when path of target field is a prefix of path of source field.
  * Also, caret must NOT be at position inside the path prefix that needs to be removed.
  * <p/>
  * 3. If neither "taking out" or "inserting" is possible, objeect entry may be simply swapped with its adjacent entry.
  * <p/>
  * 4. If there is no adjacent entry for swapping, object entry is simply swapped with adjacent line.
  *
  * @author ghik
  */
class HoconObjectEntryMover extends LineMover {
  override def checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean =
    super.checkAvailable(editor, file, info, down) && !editor.getSelectionModel.hasSelection &&
      (file match {
        case hoconFile: HoconPsiFile =>
          checkAvailableHocon(editor, hoconFile, info, down)
        case _ =>
          false
      })

  private def checkAvailableHocon(editor: Editor, file: HoconPsiFile, info: MoveInfo, down: Boolean): Boolean = {
    val document = editor.getDocument
    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset)

    if (element == null) return false

    val currentLine = document.getLineNumber(offset)

    def startLine(el: PsiElement) =
      document.getLineNumber(el.getTextRange.getStartOffset)

    def endLine(el: PsiElement) =
      document.getLineNumber(el.getTextRange.getEndOffset)

    def firstNonCommentLine(el: PsiElement) =
      document.getLineNumber(el.getTextOffset)

    def canInsertBefore(entry: HObjectEntry) = {
      val lineStart = document.getLineStartOffset(startLine(entry))
      entry.parent.exists(_.getTextRange.getStartOffset <= lineStart) &&
        entry.previousEntry.forall(_.getTextRange.getEndOffset < lineStart)
    }

    def canInsertAfter(entry: HObjectEntry) = {
      val lineEnd = document.getLineEndOffset(endLine(entry))
      entry.parent.exists(_.getTextRange.getEndOffset >= lineEnd) &&
        entry.nextEntry.forall(_.getTextRange.getStartOffset > lineEnd)
    }

    // Checks if lines occupied by this entry do not overlap with any adjacent entry or
    // some other part of enclosing object
    def movableLines(entry: HObjectEntry): Boolean =
      canInsertBefore(entry) && canInsertAfter(entry)

    // Finds ancestor object entry that can be "grabbed and moved" by current offset
    @tailrec def enclosingAnchoredEntry(el: PsiElement): Option[HObjectEntry] = el match {
      case _: PsiFile => None
      case _ if firstNonCommentLine(el) != currentLine => None
      case entry: HObjectEntry if movableLines(entry) => Some(entry)
      case _ => enclosingAnchoredEntry(el.getParent)
    }

    def isByEdge(entry: HObjectEntry) = !entry.parent.exists(_.isToplevel) && { // todo suspicious
      if (down) entry.nextEntry.forall(ne => entry.parent.exists(pp => startLine(ne) == endLine(pp)))
      else entry.previousEntry.forall(pe => entry.parent.exists(pp => endLine(pe) == startLine(pp)))
    }

    def keyString(keyedField: HKeyedField) =
      keyedField.key.map(_.getText).getOrElse("")

    def inSingleLine(entry: HObjectEntry) =
      firstNonCommentLine(entry) == endLine(entry)

    def lineRange(el: PsiElement) =
      new LineRange(startLine(el), endLine(el) + 1)

    def singleLineRange(line: Int) =
      new LineRange(line, line + 1)

    def adjacentMovableEntry(entry: HObjectEntry) =
      if (down) entry.nextEntry.filter(canInsertAfter)
      else entry.previousEntry.filter(canInsertBefore)

    def fieldToAscendOutOf(field: HObjectField): Option[(HObjectField, List[String])] =
      if (isByEdge(field)) {
        def edgeLine(element: PsiElement) =
          if (down) endLine(element) else firstNonCommentLine(element)

        def canInsert(field: HObjectField) =
          if (down) canInsertAfter(field) else canInsertBefore(field)

        field.parent.flatMap(_.prefixingField).map(_.enclosingObjectField)
          .filter(of => field.parent.exists(pp => edgeLine(of) == edgeLine(pp)) && canInsert(of))
          .map(of => (of, of.keyedField.fieldsInPathForward.map(keyString).toList))
      } else None

    def canInsertInto(field: HObjectField) =
      !inSingleLine(field) && {
        val lineToInsertAfter = if (down) firstNonCommentLine(field) else endLine(field) - 1
        file.elementsAt(document.getLineEndOffset(lineToInsertAfter)).collectFirst {
          case entries: HObjectEntries => entries.prefixingField.map(_.enclosingObjectField).contains(field)
          case _: HKeyedField => false
        } getOrElse false
      }

    def adjacentEntry(entry: HObjectEntry) =
      if (down) entry.nextEntry else entry.previousEntry

    def fieldToDescendInto(field: HObjectField): Option[(HObjectField, List[String])] =
      for {
        adjacentField <- adjacentEntry(field).collect({ case f: HObjectField => f }).filter(canInsertInto)
        prefixToRemove <- {
          val prefix = adjacentField.keyedField.fieldsInPathForward.map(keyString).toList
          val removablePrefix = field.keyedField.fieldsInPathForward.takeWhile {
            case prefixed: HPrefixedField => prefixed.subField.getTextRange.contains(offset)
            case _ => false
          }.map(keyString).toList
          if (removablePrefix.startsWith(prefix)) Some(prefix) else None
        }
      } yield (adjacentField, prefixToRemove)

    def trySpecializedFieldMove(objField: HObjectField) = {
      val sourceRange = lineRange(objField)

      fieldToAscendOutOf(objField).map { case (enclosingField, prefixToAdd) =>
        val targetRange =
          if (down) new LineRange(sourceRange.endLine, endLine(enclosingField) + 1)
          else new LineRange(startLine(enclosingField), sourceRange.startLine)
        val mod = PrefixModification(objField.getTextOffset, 0, prefixToAdd.mkString("", ".", "."))
        (sourceRange, targetRange, Some(mod))

      } orElse fieldToDescendInto(objField).map { case (adjacentField, prefixToRemove) =>
        val targetRange =
          if (down) new LineRange(sourceRange.endLine, firstNonCommentLine(adjacentField) + 1)
          else new LineRange(endLine(adjacentField), sourceRange.startLine)
        val prefixStr = prefixToRemove.mkString("", ".", ".")
        val needsGuard = document.getCharsSequence.charAt(objField.getTextOffset + prefixStr.length).isWhitespace
        val mod = PrefixModification(objField.getTextOffset, prefixStr.length, if (needsGuard) "\"\"" else "")
        (sourceRange, targetRange, Some(mod))
      }
    }

    def tryEntryMove(entry: HObjectEntry) = {
      val sourceRange = lineRange(entry)
      adjacentMovableEntry(entry).map { adjacentEntry =>
        (sourceRange, lineRange(adjacentEntry), None)
      } orElse {
        val maxLinePos = editor.offsetToLogicalPosition(document.getTextLength)
        val maxLine = if (maxLinePos.column == 0) maxLinePos.line else maxLinePos.line + 1
        val nearLine = if (down) sourceRange.endLine else sourceRange.startLine - 1

        if (nearLine >= 0 && nearLine < maxLine)
          Some((sourceRange, singleLineRange(nearLine), None))
        else None
      }
    }

    val rangesOpt: Option[(LineRange, LineRange, Option[PrefixModification])] =
      enclosingAnchoredEntry(element).flatMap {
        case objField: HObjectField =>
          trySpecializedFieldMove(objField) orElse tryEntryMove(objField)
        case include: HInclude =>
          tryEntryMove(include)
      }

    rangesOpt.foreach { case (source, target, prefixMod) =>
      info.toMove = source
      info.toMove2 = target
      info.putUserData(PrefixModKey, prefixMod)
    }
    rangesOpt.isDefined
  }

  override def beforeMove(editor: Editor, info: MoveInfo, down: Boolean): Unit =
    info.getUserData(PrefixModKey).foreach {
      case PrefixModification(offset, length, replacement) =>
        // we need to move caret manually when adding prefix exactly at caret position
        val caretModel = editor.getCaretModel
        val shouldMoveCaret = length == 0 && caretModel.getOffset == offset
        editor.getDocument.replaceString(offset, offset + length, replacement)
        if (shouldMoveCaret) {
          caretModel.moveToOffset(caretModel.getOffset + replacement.length)
        }
    }
}

object HoconObjectEntryMover {

  case class PrefixModification(offset: Int, length: Int, replacement: String)

  val PrefixModKey = new Key[Option[PrefixModification]]("PrefixMod")
}
