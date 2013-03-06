package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.util.containers.WeakHashMap
import com.intellij.openapi.editor.Editor

/**
 * @author Ksenia.Sautina
 * @since 12/19/12
 */

object WorksheetViewerInfo {
  private val allViewers =
    new WeakHashMap[Editor, List[(Editor)]]()

  def getViewer(editor: Editor): Editor = get(editor)

  def addViewer(viewer: Editor, editor: Editor) {
    synchronized {
      allViewers.get(editor) match {
        case null =>
          allViewers.put(editor, viewer :: Nil)
        case list: List[Editor] =>
          allViewers.put(editor, viewer :: list)
      }
    }
  }

  def disposeViewer(viewer: Editor, editor: Editor) {
    synchronized {
      allViewers.get(editor) match {
        case null =>
        case list: List[Editor] =>
          allViewers.put(editor, list.filter {
            case sViewer => sViewer != viewer
          })
      }
    }
  }

  private def get(editor: Editor): Editor = {
    synchronized {
      allViewers.get(editor) match {
        case null => null
        case list => list.headOption.getOrElse(null)
      }
    }
  }
}
