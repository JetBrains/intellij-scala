package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.codeStyle.CommenterForm;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.settings.ScalaSettingsBundle;

import javax.swing.*;
import java.util.List;

public final class CodeGenerationPanel extends ScalaCodeStylePanelBase {

    private final JPanel panel;
    private final CommenterForm myCommenterForm;

    // NOTE: must be public to be called from different class loader (see ScalaTabbedCodeStylePanel)
    @ApiStatus.Internal
    public CodeGenerationPanel(@NotNull CodeStyleSettings settings) {
        super(settings, ScalaSettingsBundle.message("codegeneration.panel.title"));

        myCommenterForm = new CommenterForm(ScalaLanguage.INSTANCE);
        myCommenterForm.showStandardOptions(SupportedCommenterStandardOptionNames.toArray(new String[]{}));

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createEmptyBorder(new JBInsets(0, 10, 10, 10)));
        panel.add(myCommenterForm.getCommenterPanel());
    }

    public static List<String> SupportedCommenterStandardOptionNames = List.of(
            CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_AT_FIRST_COLUMN.name(),
            CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_ADD_SPACE.name(),
            CodeStyleSettingsCustomizable.CommenterOption.BLOCK_COMMENT_AT_FIRST_COLUMN.name(),
            CodeStyleSettingsCustomizable.CommenterOption.BLOCK_COMMENT_ADD_SPACE.name()
    );

    @Override
    public void apply(@NotNull CodeStyleSettings settings) {
        if (isModified(settings)) myCommenterForm.apply(settings);
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
        return myCommenterForm.isModified(settings);
    }

    @Override
    protected JComponent getPanelInner() {
        return panel;
    }

    @Override
    protected void resetImpl(@NotNull CodeStyleSettings settings) {
        myCommenterForm.reset(settings);
    }
}
