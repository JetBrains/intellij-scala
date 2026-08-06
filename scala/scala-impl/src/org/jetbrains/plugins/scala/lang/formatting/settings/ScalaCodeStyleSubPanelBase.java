package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ScalaCodeStyleSubPanelBase extends CodeStyleAbstractPanel {

    protected ScalaCodeStyleSubPanelBase(@NotNull CodeStyleSettings settings) {
        super(settings);
    }

    @Override
    protected int getRightMargin() {
        return 0;
    }

    @Override
    protected @Nullable EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
        return null;
    }

    @Override
    protected @NotNull FileType getFileType() {
        return null;
    }

    @Override
    protected @NonNls
    @Nullable String getPreviewText() {
        return null;
    }
}
