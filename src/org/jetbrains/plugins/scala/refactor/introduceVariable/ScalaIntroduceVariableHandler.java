package org.jetbrains.plugins.scala.refactor.introduceVariable;

import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.highlighting.HighlightManager;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaValidator;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableDialogInterface;
import org.jetbrains.plugins.scala.lang.refactoring.NameSuggester;
import org.jetbrains.plugins.scala.ScalaBundle;

import java.util.ArrayList;

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */

public class ScalaIntroduceVariableHandler extends ScalaIntroduceVariableBase {
  public void showErrorMessage(String text, Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(text);
    RefactoringMessageDialog dialog = new RefactoringMessageDialog("Introduce variable refactoring", text,
            HelpID.INTRODUCE_VARIABLE, "OptionPane.errorIcon", false, project);
    dialog.show();
  }

  public ScalaIntroduceVariableDialogInterface getDialog(final Project project, Editor editor, ScExpression expr,
                                                             ScType type, ScExpression[] occurrences, boolean decalreVariable,
                                                             ScalaValidator validator) {
    // Add occurences highlighting
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = null;
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (occurrences.length > 1) {
        highlightManager.addOccurrenceHighlights(editor, occurrences, attributes, true, highlighters);
      }
    }

    String[] possibleNames = NameSuggester.suggestNames(expr, validator);
    ScalaIntroduceVariableDialogInterface dialog = new ScalaIntroduceVariableDialog(project, type, occurrences.length, validator, possibleNames);
    dialog.show();
    if (!dialog.isOK()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"));
      }
    } else {
      if (editor != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return dialog;
  }

  public boolean reportConflicts(String[] conflicts, Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }
}
