package org.jetbrains.plugins.scala.codeInsight

import com.intellij.openapi.actionSystem.{KeyboardShortcut, Shortcut}
import com.intellij.openapi.editor.{EditorCustomElementRenderer, InlayModel}
import com.intellij.openapi.keymap.{Keymap, KeymapManager}
import com.intellij.openapi.util.{Key, TextRange}
import javax.swing.KeyStroke
import org.jetbrains.plugins.scala.annotator.hints.Hint

import scala.jdk.CollectionConverters._
import scala.collection.mutable

package object implicits {
  private val ScalaImplicitHintKey = Key.create[Boolean]("SCALA_IMPLICIT_HINT")

  private[implicits] type Inlay = com.intellij.openapi.editor.Inlay[_ <: EditorCustomElementRenderer]

  implicit class Model(private val model: InlayModel) extends AnyVal {
    def inlaysIn(range: TextRange): collection.Seq[Inlay] =
      model.getInlineElementsInRange(range.getStartOffset, range.getEndOffset, classOf[EditorCustomElementRenderer])
        .asScala
        .filter(ScalaImplicitHintKey.isIn)

    def add(hint: Hint): Unit = Option(
      ImplicitHint.addTo(hint, model)).foreach(_.putUserData(ScalaImplicitHintKey, true)
    )
  }

  class ShortcutManager {
    case class ManagedShortcut private(shortCut: Shortcut)

    private val allShortCuts = mutable.Buffer.empty[Shortcut]

    protected def register(shortcut: Shortcut): ManagedShortcut = {
      allShortCuts += shortcut
      ManagedShortcut(shortcut)
    }

    def setShortcuts(id: String, shortcuts: Seq[this.ManagedShortcut]): Unit = {
      val keymap = KeymapManager.getInstance().getActiveKeymap
      removeAllShortcuts(id)
      shortcuts.foreach(s => keymap.addShortcut(id, s.shortCut))
    }

    def removeAllShortcuts(id: String): Unit = {
      removeAllShortcuts(KeymapManager.getInstance().getActiveKeymap, id)
    }
    private def removeAllShortcuts(keymap: Keymap, id: String): Unit = {
      allShortCuts.foreach(keymap.removeShortcut(id, _))
    }
  }

  object ImplicitShortcuts extends ShortcutManager {
    val EnableShortcuts = Seq(
      register(new KeyboardShortcut(KeyStroke.getKeyStroke("control alt shift EQUALS"), null)),
      register(new KeyboardShortcut(KeyStroke.getKeyStroke("control alt shift ADD"), null)),
    )

    val DisableShortcuts = Seq(
      register(new KeyboardShortcut(KeyStroke.getKeyStroke("control alt shift MINUS"), null)),
      register(new KeyboardShortcut(KeyStroke.getKeyStroke("control alt shift SUBTRACT"), null)),
    )
  }


  def pairFor[T](element: T, elements: Seq[T], isOpening: T => Boolean, isClosing: T => Boolean): Option[T] = {
    def pairIn(elements: Seq[T]) = {
      var balance = 0
      val remainder = elements.dropWhile(_ != element).dropWhile { it =>
        if (isOpening(it)) balance += 1
        if (isClosing(it)) balance -= 1
        balance != 0
      }
      remainder.headOption
    }

    if (isOpening(element)) pairIn(elements)
    else if (isClosing(element)) pairIn(elements.reverse)
    else None
  }
}
