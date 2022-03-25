package org.jetbrains.plugins.scala.codeInspection.unusedInspections;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle;

import javax.swing.*;

public final class ScalaUnusedDeclarationInspection extends ScalaUnusedDeclarationInspectionBase {
    public boolean reportPublicDeclarationsEnabled = true;

    public boolean isReportPublicDeclarationsEnabled() {
        return reportPublicDeclarationsEnabled;
    }

    public void setReportPublicDeclarationsEnabled(boolean enabled) {
        this.reportPublicDeclarationsEnabled = enabled;
    }

    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                ScalaInspectionBundle.message("name.unused.declaration.report.public.declarations"),
                this,
                "reportPublicDeclarationsEnabled"
        );
    }
}