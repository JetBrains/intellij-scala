package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;

import javax.swing.*;

abstract public class ScalaCodeStylePanelBase extends CodeStyleAbstractPanel {

    @Nullable
    private final String myTabTitle;
    private JComponent myPanel;

    protected ScalaCodeStylePanelBase(@NotNull CodeStyleSettings settings, @NotNull String tabTitle) {
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
    protected final EditorHighlighter createHighlighter(@NotNull EditorColorsScheme colors) {
        FileType fileType = getFileType();
        return FileTypeEditorHighlighterProviders.getInstance()
                .forFileType(fileType)
                .getEditorHighlighter(null, fileType, null, colors);
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

    @Override
    public final JComponent getPanel() {
        if (myPanel == null) {
            JComponent contentPanel = getPanelInner();
            myPanel = ScrollPaneFactory.createScrollPane(contentPanel, true);
        }
        return myPanel;
    }

    abstract protected JComponent getPanelInner();
}
