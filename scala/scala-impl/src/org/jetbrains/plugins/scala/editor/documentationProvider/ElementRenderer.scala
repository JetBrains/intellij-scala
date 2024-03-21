package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.daemon.impl.{AnnotationHolderImpl, AnnotationSessionImpl}
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme, TextAttributesKey}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.highlighter.{ScalaColorSchemeAnnotator, ScalaSyntaxHighlighterFactory}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScThisReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt, ScalaFeatures}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.text.ClassPrinter

import java.awt.Font
import scala.jdk.CollectionConverters.ListHasAsScala

// Proof of concept implementation, #SCL-22046
object ElementRenderer {
  def toHtml(e: PsiElement, context: PsiElement): String = {
    val printer = new ClassPrinter(isScala3 = e.isInScala3File, extendsSeparator = "\n")
    val text = try {
      ScalaApplicationSettings.PRECISE_TEXT = true
      printer.textOf(e).split("\n").map(_.trim).mkString("\n")
    } finally {
      ScalaApplicationSettings.PRECISE_TEXT = false
    }
    val file = ScalaPsiElementFactory.createScalaFileFromText(text, e.module.map(_.features).getOrElse(ScalaFeatures.default))(e.getProject)
    file.children.foreach(_.asInstanceOf[ScalaPsiElement].context = context)

    val highlighted = highlight(file, EditorColorsManager.getInstance.getGlobalScheme)
    val filtered = filter(highlighted)
    toHtml(filtered).mkString
  }

  private def highlight(file: PsiFile, colorScheme: EditorColorsScheme): Seq[(PsiElement, TextAttributes)] = {
    val highlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(file.getProject, file = null, language = file.getLanguage)
    val annotator = new ScalaColorSchemeAnnotator()
    val elements = file.elements.toSeq

    val rangeToKey: Seq[(TextRange, TextAttributesKey)] = AnnotationSessionImpl.computeWithSession(file, false, annotator, {
      case holder: AnnotationHolderImpl =>
        elements.flatMap { e =>
          holder.runAnnotatorWithContext(e)
          holder.asScala.map(it => (TextRange.create(it.getStartOffset, it.getEndOffset), it.getTextAttributes))
        }
      case _ => Seq.empty
    })

    val leafElementToKeys = elements.filter(_.is[LeafPsiElement]).map { e =>
      val annotations = rangeToKey.filter(_._1.contains(e.getTextRange)).map(_._2) // TODO Optimize
      (e, highlighter.getTokenHighlights(e.getNode.getElementType) ++ annotations)
    }

    leafElementToKeys.map { case (e, keys) =>
      (e, keys.map(colorScheme.getAttributes).foldLeft(new TextAttributes())(TextAttributes.merge))
    }
  }

  private def filter(elements: Seq[(PsiElement, TextAttributes)]): Seq[(PsiElement, TextAttributes)] = {
    val es = elements
      .filter(!_._1.getParent.is[ScThisReference])
      .filter(p => p._1.getParent.asOptionOf[ScStableCodeReference].forall(_.getNextSibling == null && p._1.getNextSibling == null))
    val i = es.map(_._1.getText).indexOfSlice(Seq(" ", "=", " ", "???"))
    if (i == -1) es else es.take(i) ++ es.drop(i + 4)
  }

  // Customized to match the test cases
  private def toHtml(elements: Seq[(PsiElement, TextAttributes)]): Seq[String] = elements.map { case (e, a) =>
    val text = e.getText.replaceAll("\"", "&quot;").replaceAll("<", "&lt;").replaceAll("(?<!=)>", "&gt;") // TODO Escape => as well

    if (e.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER && e.getParent.is[ScParameter]) text else { // TODO Highlight parameters
      val link = {
        val path = e.parents.takeWhile(_.is[ScReference]).toSeq.lastOption.map(_.getText).filter(_.startsWith("_root_."))
        path.map(path => s"<a href=\"psi_element://${path.stripPrefix("_root_.")}\"><code>$text</code></a>").getOrElse(text)
      }
      if (a.getForegroundColor == null && a.getFontType == Font.PLAIN && !link.startsWith("<a href=")) link else { // TODO Omit style="" for links
        val color = Option(a.getForegroundColor).map(c => "color:" + f"#${c.getRed}%02x${c.getGreen}%02x${c.getBlue}%02x" + ";").mkString
        val font = if (a.getFontType == Font.BOLD) "font-weight:bold;" else if (a.getFontType == Font.ITALIC) "font-style:italic;" else ""
        s"<span style=\"$color$font\">" + link + "</span>"
      }
    }
  }
}
