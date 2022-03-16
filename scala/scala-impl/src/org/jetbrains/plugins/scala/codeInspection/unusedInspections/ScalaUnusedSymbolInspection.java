package org.jetbrains.plugins.scala.codeInspection.unusedInspections;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle;

import javax.swing.*;

public class ScalaUnusedSymbolInspection extends ScalaUnusedSymbolInspectionBase {
    public static boolean reportPublicSymbols = true;

    public boolean isReportPublicSymbols() {
        return reportPublicSymbols;
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
            ScalaInspectionBundle.message("name.unused.symbol.report.public.symbols"),
            this,
            "reportPublicSymbols"
        );
    }
}