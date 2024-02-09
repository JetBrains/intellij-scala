package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationSessionImpl
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.highlighter.{ScalaColorSchemeAnnotator, ScalaSyntaxHighlighterFactory}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.text.ClassPrinter

import java.awt.Font
import scala.jdk.CollectionConverters.ListHasAsScala

// Proof of concept implementation, #SCL-22046
object ElementRenderer {
  def toHtml(e: PsiElement): String = {
    val printer = new ClassPrinter(isScala3 = e.isInScala3File)
    val text = try {
      ScalaApplicationSettings.PRECISE_TEXT = true
      printer.textOf(e).trim
    } finally {
      ScalaApplicationSettings.PRECISE_TEXT = false
    }
    val file = ScalaPsiElementFactory.createScalaFileFromText(text, ScalaFeatures.default)(e.getProject)
    file.children.foreach(_.asInstanceOf[ScalaPsiElement].context = e.getContext)

    val highlighted = highlight(file, EditorColorsManager.getInstance.getGlobalScheme)
    val filtered = filter(highlighted)
    toHtml(filtered).mkString
  }

  private def highlight(file: PsiFile, colorScheme: EditorColorsScheme): Seq[(PsiElement, TextAttributes)] = {
    val highlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(file.getProject, file = null, language = file.getLanguage)
    val annotator = new ScalaColorSchemeAnnotator()
    val elements = file.elements.toSeq

    val rangeToKey = AnnotationSessionImpl.computeWithSession(file, false, holder => {
      elements.flatMap { e =>
        holder.runAnnotatorWithContext(e, annotator)
        holder.asScala.map(it => (TextRange.create(it.getStartOffset, it.getEndOffset), it.getTextAttributes))
      }
    }).groupMap(_._1)(_._2)

    val leafElementToKeys = elements.filter(_.is[LeafPsiElement]).map { e =>
      (e, highlighter.getTokenHighlights(e.getNode.getElementType) ++ rangeToKey.getOrElse(e.getTextRange, List.empty))
    }

    leafElementToKeys.map { case (e, keys) =>
      (e, keys.map(colorScheme.getAttributes).foldLeft(new TextAttributes())(TextAttributes.merge))
    }
  }

  private def filter(elements: Seq[(PsiElement, TextAttributes)]): Seq[(PsiElement, TextAttributes)] = {
    val es = elements.filter(p => p._1.getParent.asOptionOf[ScStableCodeReference].forall(_.getNextSibling == null && p._1.getNextSibling == null))
    val i = es.map(_._1.getText).indexOfSlice(Seq(" ", "=", " ", "???"))
    if (i == -1) es else es.take(i) ++ es.drop(i + 4)
  }

  private def toHtml(elements: Seq[(PsiElement, TextAttributes)]): Seq[String] = elements.map { case (e, a) =>
    val link = if (!e.getParent.isInstanceOf[ScReference]) e.getText else {
      val path = e.parents.takeWhile(_.is[ScReference]).toSeq.last.getText.stripPrefix("_root_.")
      s"<a href=\"psi_element://${path}\"><code>${e.getText}</code></a>"
    }
    if (a.getForegroundColor == null && a.getFontType == Font.PLAIN) link else {
      val color = Option(a.getForegroundColor).map(c => "color:" + f"#${c.getRed}%02x${c.getGreen}%02x${c.getBlue}%02x" + ";").mkString
      val font = if (a.getFontType == Font.BOLD) "font-weight:bold;" else if (a.getFontType == Font.ITALIC) "font-style:italic;" else ""
      s"<span style=\"$color$font\">" + link + "</span>"
    }
  }
}
