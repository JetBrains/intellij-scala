package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme, EditorFontType}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.{SimpleColoredComponent, SimpleTextAttributes}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.conversion.copy.BindingCellRenderer.renderImportStatementText
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.Associations.BindingLike
import org.jetbrains.plugins.scala.project.ScalaFeatures

import java.awt.{Component, Font}
import javax.swing.{JList, ListCellRenderer}

/**
 * Based on [[com.intellij.ide.util.FQNameCellRenderer]]
 */
//noinspection ReferencePassedToNls,ScalaExtractStringToBundle
class BindingCellRenderer(
  features: ScalaFeatures,
  colorScheme: EditorColorsScheme,
  project: Project
) extends SimpleColoredComponent
  with ListCellRenderer[BindingLike] {

  private val FONT: Font = {
    val scheme: EditorColorsScheme = EditorColorsManager.getInstance.getGlobalScheme
    scheme.getFont(EditorFontType.PLAIN)
  }

  setOpaque(true)


  //NOTE: we don't add any icon because we don't know to which the path corresponds
  override def getListCellRendererComponent(
    list: JList[_ <: BindingLike],
    binding: BindingLike,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component = {
    clear()

    val useNewImportsRepresentation = true
    if (useNewImportsRepresentation) {
      val parts = renderImportStatementText(binding, features, project, colorScheme)
      parts.foreach { case (text, attributes) =>
        val simpleAttributes = if (attributes != null)
          SimpleTextAttributes.fromTextAttributes(attributes)
        else
          SimpleTextAttributes.REGULAR_ATTRIBUTES
        append(text, simpleAttributes)
      }
    } else {
      append(binding.path)
    }

    setFont(FONT)
    setBackground(if (isSelected) list.getSelectionBackground else list.getBackground)
    setForeground(if (isSelected) list.getSelectionForeground else list.getForeground)

    this
  }
}

object BindingCellRenderer {

  private def renderImportStatementText(
    binding: BindingLike,
    features: ScalaFeatures,
    project: Project,
    colorScheme: EditorColorsScheme,
  ): Seq[(String, TextAttributes)] = {
    val importText = buildImportStatementText(binding, features)
    renderUsingSyntaxHighlighter(importText, features, project, colorScheme)
  }

  @TestOnly
  def buildImportStatementText(binding: BindingLike, features: ScalaFeatures): String =
    binding.aliasName match {
      case Some(aliasName) =>
        //append(binding.path)
        val (init, last) = binding.getInitAndLast

        val useScala3Syntax = features.`Scala 3 renaming imports`
        val addBraces = !useScala3Syntax
        val prefix = if (addBraces) "{" else ""
        val suffix = if (addBraces) "}" else ""
        val arrow =
          if (useScala3Syntax) "as"
          else "=>"

        s"import $init.$prefix$last $arrow $aliasName$suffix"
      case None =>
        s"import ${binding.path}"
    }

  private def renderUsingSyntaxHighlighter(
    fileText: String,
    features: ScalaFeatures,
    project: Project,
    colorScheme: EditorColorsScheme,
  ): Seq[(String, TextAttributes)] = {
    val file = ScalaPsiElementFactory.createScalaFileFromText(fileText, features)(project)
    val highlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(project, file = null, language = file.getLanguage)

    val leafElements = file.elements.filter(_.is[LeafPsiElement])
    leafElements.map { e =>
      val keys = highlighter.getTokenHighlights(e.elementType)
      val attributes = keys.map(colorScheme.getAttributes).foldLeft(new TextAttributes())(TextAttributes.merge)
      (e.getText, attributes)
    }.toSeq
  }
}
