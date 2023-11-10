package org.jetbrains.plugins.scala.annotator.hints

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.colors.{EditorColorsScheme, EditorFontType}
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.annotator.hints.Hint.MenuProvider
import org.jetbrains.plugins.scala.extensions.ObjectExt

import java.awt.Insets

case class Hint(parts: Seq[Text],
                element: PsiElement,
                suffix: Boolean,
                menu: MenuProvider = MenuProvider.NoMenu,
                margin: Option[Insets] = None,
                relatesToPrecedingElement: Boolean = false,
                offsetDelta: Int = 0) { //gives more natural behaviour

  // We want auto-generate apply() and copy() methods, but reference-based equality
  override def equals(obj: scala.Any): Boolean = obj.asOptionOf[AnyRef].exists(eq)
}

object Hint {
  def leftInsetLikeChar(char: Char, editor: Option[Editor] = None)(implicit scheme: EditorColorsScheme): Option[Insets] =
    widthOf(char, editor).map(new Insets(0, _, 0, 0))

  // TODO Can we detect a "current" editor somehow?
  private def widthOf(char: Char, editor: Option[Editor])(implicit scheme: EditorColorsScheme) =
    editor.orElse(EditorFactory.getInstance().getAllEditors.headOption)
      .map(_.getComponent.getFontMetrics(scheme.getFont(EditorFontType.PLAIN)).charWidth(char))


  final class MenuProvider private(
    @Nullable val groupIdOrNull: String,
    @Nullable val actionGroupOrNull: ActionGroup,
  )

  object MenuProvider {
    val NoMenu: MenuProvider = new MenuProvider(null, null)

    def apply(groupId: String): MenuProvider = new MenuProvider(groupId, null)
    def apply(actionGroup: ActionGroup): MenuProvider = new MenuProvider(null, actionGroup)
  }
}