package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.codeStyle.CommenterForm;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaLanguage;

import javax.swing.*;

public final class CodeGenerationPanel extends ScalaCodeStylePanelBase {

    private final JPanel panel;
    private final CommenterForm myCommenterForm;

    CodeGenerationPanel(@NotNull CodeStyleSettings settings) {
        super(settings, ScalaBundle.message("codegeneration.panel.title"));

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createEmptyBorder(new JBInsets(0, 10, 10, 10)));

        myCommenterForm = new CommenterForm(ScalaLanguage.INSTANCE);
        // TODO: show also BLOCK_COMMENT_ADD_SPACE and use it
        myCommenterForm.showStandardOptions(SupportedCommenterStandardOptionNames.toArray(new String[]{}));

        panel.add(myCommenterForm.getCommenterPanel());
    }

    public static ImmutableList<String> SupportedCommenterStandardOptionNames = ContainerUtil.immutableList(
            CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_AT_FIRST_COLUMN.name(),
            CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_ADD_SPACE.name(),
            CodeStyleSettingsCustomizable.CommenterOption.BLOCK_COMMENT_AT_FIRST_COLUMN.name()
    );

    @Override
    public void apply(CodeStyleSettings settings) {
        if (isModified(settings))
            myCommenterForm.apply(settings);
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
    protected void resetImpl(CodeStyleSettings settings) {
        myCommenterForm.reset(settings);
    }
}
