package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;

abstract public class ScalaCodeStylePanelBase extends CodeStyleAbstractPanel {

    @Nullable
    private final String myTabTitle;

    protected ScalaCodeStylePanelBase(@NotNull CodeStyleSettings settings, @NotNull @NonNls String tabTitle) {
        super(settings);
        myTabTitle = tabTitle;
    }

    protected ScalaCodeStylePanelBase(@NotNull CodeStyleSettings settings) {
        super(settings);
        myTabTitle = null;
    }

    @NotNull
    @Override
    protected final String getTabTitle() {
        return myTabTitle != null ? myTabTitle : super.getTabTitle();
    }

    @NotNull
    @Override
    protected final EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
        return new ScalaEditorHighlighter(scheme);
    }

    @NotNull
    @Override
    protected final FileType getFileType() {
        return ScalaFileType.INSTANCE;
    }

    @Nullable
    @Override
    protected final String getPreviewText() {
        return null;
    }

    @Override
    protected final int getRightMargin() {
        return 0;
    }
}
