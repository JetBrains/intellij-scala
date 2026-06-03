package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.codeInsight.lookup.{Lookup, LookupEvent, LookupListener, LookupManagerListener}
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.editor.AutoBraceUtils._
import org.jetbrains.plugins.scala.editor.typedHandler.AutoBraceInsertionTools._
import org.jetbrains.plugins.scala.editor.typedHandler.AutoBraceLookupListenerService.AutoBraceLookupListener
import org.jetbrains.plugins.scala.editor.{DocumentExt, EditorExt}
import org.jetbrains.plugins.scala.extensions._

/**
 * This Service watches for completion popups and interferes in the very rare occasion
 * that the user starts typing something that, at the moment, looks like an identifier
 * but could very well become a continuation of a previous construct:
 *
 * {{{
 *   if (cond)
 *     expr
 *     els<caret>   // this could become `else`
 * }}}
 *
 * In this case we cannot insert braces because those wouldn't make sense if the user
 * wanted to write `else`. This logic is normally correctly handled by
 * `ScalaTypedHandler.beforeCharTyped` and `AutoBraceInsertionTools.findAutoBraceInsertionOpportunity`,
 * but unfortunately these are not called when a completion popup is open.
 *
 * Because of that we watch for completeion popups with this service and retroactively
 * 'fix' the auto braces when the dialog either chooses a completion item or gets canceled.
 */
@Service(Array(Service.Level.PROJECT))
final class AutoBraceLookupListenerService(project: Project) extends Disposable {
  locally {
    project.getMessageBus.connect(this)
      .subscribe(LookupManagerListener.TOPIC,
        new LookupManagerListener {
          override def activeLookupChanged(oldLookup: Lookup, newLookup: Lookup): Unit = {
            if (newLookup != null && AutoBraceInsertionTools.autoBraceInsertionActivated) {
              newLookup.addLookupListener(new AutoBraceLookupListener)
            }
          }
        }
      )
  }

  override def dispose(): Unit = ()
}

object AutoBraceLookupListenerService {
  private class AutoBraceLookupListener extends LookupListener {
    private var autoBraceInsertionInfo = Option.empty[(AutoBraceInsertionInfo, RangeMarker)]

    override def lookupCanceled(event: LookupEvent): Unit = {
      checkForAutoBraceInsertion(event).foreach(doAutoBraceInsertion(event, _))
    }

    override def beforeItemSelected(event: LookupEvent): Boolean = {
      // Before the item is inserted we assess the situation with the normal `findAutoBraceInsertionOpportunity`
      // logic and save the information to be used again after the completion has finished it's own insertion.
      // Unfortunately we cannot add braces before here because that completely destroys the completion insertion
      // logic in case \t is used to complete the completion lookup.
      // So instead, we save the info and add a marker at the position of the closing brace,
      // so we can fix the info later when `itemSelected` is called.
      autoBraceInsertionInfo =
        for (info <- checkForAutoBraceInsertion(event)) yield {
          val document = event.getLookup.getEditor.getDocument
          val marker = document.createRangeMarker(info.closingBraceOffset, info.closingBraceOffset)
          marker.setGreedyToRight(true)
          (info, marker)
        }
      super.beforeItemSelected(event)
    }

    override def itemSelected(event: LookupEvent): Unit = {
      for ((info, marker) <- autoBraceInsertionInfo) {
        val fixedInfo = info.copy(
          closingBraceOffset = try marker.getEndOffset finally marker.dispose(),
          inputOffset = event.getLookup.getEditor.offset,
          // We know that we don't need fake input because the affected line contains now the completed item
          // and as such the indent cannot be screwed up by the formatter.
          needsFakeInput = false
        )

        doAutoBraceInsertion(event, fixedInfo)
      }
    }

    private def checkForAutoBraceInsertion(event: LookupEvent): Option[AutoBraceInsertionInfo] = {
      val lookup = event.getLookup

      val editor = lookup.getEditor
      val document = editor.getDocument
      val caret = editor.offset

      val line = document.lineTextAt(caret)
      val firstNonWs = line.indexWhere(!_.isWhitespace)
      if (firstNonWs == -1 || !startsWithContinuationPrefix(line.substring(firstNonWs))) {
        return None
      }

      val project = lookup.getProject
      val file = lookup.getPsiFile
      if (file == null) {
        return None
      }

      // if the selected item is something other than a continuation,
      // just delude findAutoBraceInsertionOpportunity with a character that certainly cannot be part of a continuation
      val currentItem = event.getItem.toOption.collect {
        case item if !continuesConstructAfterIndentationContext(item.getLookupString) => 'x'
      }

      val element = file.findElementAt(caret)
      findAutoBraceInsertionOpportunity(currentItem, caret, element)(project, file, editor)
    }

    private def doAutoBraceInsertion(event: LookupEvent, info: AutoBraceInsertionInfo): Unit = {
      val lookup = event.getLookup
      val project = lookup.getProject
      inWriteCommandAction(
        insertAutoBraces(info)(project, lookup.getPsiFile, lookup.getEditor)
      )(project)
    }
  }
}
