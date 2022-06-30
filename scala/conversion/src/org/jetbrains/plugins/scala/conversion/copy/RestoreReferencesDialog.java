package org.jetbrains.plugins.scala.conversion.copy;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ui.UIUtil.ComponentStyle.SMALL;
import static com.intellij.util.ui.UIUtil.FontColor.BRIGHTER;

@SuppressWarnings(value = "unchecked")
class RestoreReferencesDialog extends DialogWrapper {
  private final String[] myNamedElements;
  private JList<String> myList;
  private List<String> mySelectedElements = Collections.emptyList();
  private boolean myContainsClassesOnly = true;

  public RestoreReferencesDialog(final Project project, final String[] elements) {
    super(project, true);
    myNamedElements = elements;
    for (Object element : elements) {
      if (!(element instanceof PsiClass)) {
        myContainsClassesOnly = false;
        break;
      }
    }
    if (myContainsClassesOnly) {
      setTitle(CodeInsightBundle.message("dialog.import.on.paste.title"));
    }
    else {
      setTitle(CodeInsightBundle.message("dialog.import.on.paste.title2"));
    }
    init();

    myList.setSelectionInterval(0, myNamedElements.length - 1);
  }

  @Override
  protected void doOKAction() {
    mySelectedElements = myList.getSelectedValuesList();
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    myList = new JBList<>(myNamedElements);
    myList.setCellRenderer(new FQNameCellRenderer());
    panel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);

    panel.add(new JBLabel(myContainsClassesOnly ?
                          CodeInsightBundle.message("dialog.paste.on.import.text") :
                          CodeInsightBundle.message("dialog.paste.on.import.text2"), SMALL, BRIGHTER), BorderLayout.NORTH);

    final JPanel buttonPanel = new JPanel(new VerticalFlowLayout());
    final JButton okButton = new JButton(CommonBundle.getOkButtonText());
    getRootPane().setDefaultButton(okButton);
    buttonPanel.add(okButton);
    final JButton cancelButton = new JButton(CommonBundle.getCancelButtonText());
    buttonPanel.add(cancelButton);

    panel.setPreferredSize(new Dimension(500, 400));

    return panel;
  }


  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.codeInsight.editorActions.RestoreReferencesDialog";
  }

  public List<String> getSelectedElements(){
    return mySelectedElements;
  }
}
