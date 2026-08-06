package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.HintUtilsKt._
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.settings.{Diff, ParameterNameHintsSettings}
import com.intellij.lang.{LangBundle, Language}
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.BuilderKt._
import com.intellij.ui.dsl.builder.{AlignX, BottomGap, HyperlinkEventAction, Panel, Row, UtilsKt}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints

import java.awt.{Component, Dimension}
import java.util
import javax.swing.{JComponent, JLabel}
import scala.jdk.CollectionConverters.SetHasAsJava
import scala.language.implicitConversions

/** A copy of [[com.intellij.codeInsight.hints.ExcludeListDialog]] that takes [[provider]] as a parameter instead of looking it up statically. */
private class ExcludeListDialog(language: Language, provider: InlayParameterHintsProvider, patternToAdd: String = null) extends DialogWrapper(null: Project) {
  private var myEditor: EditorTextField = _
  private var myPatternsAreValid = true

  locally {
    setTitle(CodeInsightBundle.message("settings.inlay.parameter.hints.exclude.list"))
    init()
  }

  override def createCenterPanel(): JComponent = {
    val blackList = getLanguageExcludeList
    val finalText = if (patternToAdd != null) blackList + "\n" + patternToAdd else blackList
    val editorTextField = createExcludeListEditorField(finalText)
    editorTextField.setAlignmentX(Component.LEFT_ALIGNMENT)
    editorTextField.addDocumentListener(new DocumentListener {
      override def documentChanged(e: DocumentEvent): Unit = {
        updateOkEnabled(editorTextField)
      }
    })
    updateOkEnabled(editorTextField)

    myEditor = editorTextField

    @inline implicit def kotlinUnitDiscarding(v: Any): kotlin.Unit = kotlin.Unit.INSTANCE
    def row(f: Row => Unit)(implicit p: Panel) = p.row(null: JLabel, (r => f(r)): kotlin.jvm.functions.Function1[Row, kotlin.Unit])

    panel { implicit p =>
      row { r =>
        r.link(LangBundle.message("action.link.reset"), _ =>
          setLanguageExcludelistToDefault()
        ).align(AlignX.RIGHT.INSTANCE)
      }
      row { r =>
        r.cell(editorTextField)
          .align(AlignX.FILL.INSTANCE)
      }.bottomGap(BottomGap.SMALL)
      baseLanguageComment(provider).foreach { it =>
        row { r =>
          r.comment(it, UtilsKt.MAX_LINE_LENGTH_WORD_WRAP, HyperlinkEventAction.HTML_HYPERLINK_INSTANCE)
        }
      }
      row { r =>
        r.comment(getExcludeListExplanationHTML, UtilsKt.MAX_LINE_LENGTH_WORD_WRAP, HyperlinkEventAction.HTML_HYPERLINK_INSTANCE)
      }
    }
  }

  private def baseLanguageComment(provider: InlayParameterHintsProvider): Option[String] =
    Option(provider.getBlackListDependencyLanguage)
      .map(it => CodeInsightBundle.message("inlay.hints.base.exclude.list.description", it.getDisplayName))

  private def setLanguageExcludelistToDefault(): Unit = {
    val defaultExcludeList = provider.getDefaultBlackList
    myEditor.setText(StringUtil.join(defaultExcludeList, "\n"))
  }

  private def updateOkEnabled(editorTextField: EditorTextField): Unit = {
    val text = editorTextField.getText
    val invalidLines = getExcludeListInvalidLineNumbers(text)
    myPatternsAreValid = invalidLines.isEmpty

    getOKAction.setEnabled(myPatternsAreValid)

    val editor = editorTextField.getEditor
    if (editor != null) {
      highlightErrorLines(invalidLines, editor)
    }
  }

  override def doOKAction(): Unit = {
    super.doOKAction()
    val excludeList = myEditor.getText
    storeExcludeListDiff(language, excludeList)
  }

  private def storeExcludeListDiff(language: Language, text: String): Unit = {
    val updatedExcludeList = text.split("\n").filter(_.trim.nonEmpty).toSet.asJava

    val defaultExcludeList = provider.getDefaultBlackList
    val diff = Diff.Builder.build(defaultExcludeList, updatedExcludeList)
    ParameterNameHintsSettings.getInstance.setExcludeListDiff(getLanguageForSettingKey(language), diff)
    ImplicitHints.updateInAllEditors()
  }

  private def getLanguageExcludeList: String = {
    val diff = ParameterNameHintsSettings.getInstance().getExcludeListDiff(getLanguageForSettingKey(language))
    val excludeList = diff.applyOn(provider.getDefaultBlackList)
    StringUtil.join(excludeList, "\n")
  }

  private def createExcludeListEditorField(text: String): EditorTextField = {
    val document = EditorFactory.getInstance().createDocument(text)
    val field = new EditorTextField(document, null, FileTypes.PLAIN_TEXT, false, false)
    field.setPreferredSize(new Dimension(400, 350))
    field.addSettingsProvider { editor =>
      editor.setVerticalScrollbarVisible(true)
      editor.setHorizontalScrollbarVisible(true)
      editor.getSettings.setAdditionalLinesCount(2)
      highlightErrorLines(getExcludeListInvalidLineNumbers(text), editor)
    }
    field
  }

  private def highlightErrorLines(lines: util.List[Integer], editor: Editor): Unit = {
    val document = editor.getDocument
    val totalLines = document.getLineCount

    val model = editor.getMarkupModel
    model.removeAllHighlighters()
    lines.stream()
      .filter { current => current < totalLines }
      .forEach { line => model.addLineHighlighter(CodeInsightColors.ERRORS_ATTRIBUTES, line, HighlighterLayer.ERROR) }
  }

  private def getExcludeListExplanationHTML: String =
    provider.getBlacklistExplanationHTML
}