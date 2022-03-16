package org.jetbrains.plugins.scala.codeInspection.unusedInspections;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle;

import javax.swing.*;

public final class ScalaUnusedSymbolInspection extends ScalaUnusedSymbolInspectionBase {
    public boolean reportPublicSymbols = true;

    public boolean isReportPublicSymbols() {
        return reportPublicSymbols;
    }

    public void setReportPublicSymbols(boolean reportPublicSymbols) {
        this.reportPublicSymbols = reportPublicSymbols;
    }

    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
            ScalaInspectionBundle.message("name.unused.symbol.report.public.symbols"),
            this,
            "reportPublicSymbols"
        );
    }
}