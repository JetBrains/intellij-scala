package org.jetbrains.plugins.scala.lang.refactoring.extractTrait;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Nikolay.Tropin
 * 2014-05-23
 */
public class ScalaExtractTraitDialog extends DialogWrapper {

  private static final String REFACTORING_NAME = ScalaBundle.message("extract.trait.name");
  private static final String DESTINATION_PACKAGE_RECENT_KEY = "ExtractTrait.RECENT_KEYS";

  private final ScTemplateDefinition mySourceClass;
  private final Project myProject;
  private final List<ScalaExtractMemberInfo> myMembers;
  private JPanel contentPane;
  private JTextField mySourceClassFld;
  private JTextField traitNameFld;
  private JLabel sourceNameLbl;
  private JLabel traitNameLbl;
  private JPanel memberSelectionPane;
  private JPanel packageSelectionPane;
  private JLabel packageLbl;
  private PackageNameReferenceEditorCombo myPackageNameField;

  public ScalaExtractTraitDialog(Project project, ScTemplateDefinition sourceClass) {
    super(project, false);
    mySourceClass = sourceClass;
    myProject = mySourceClass.getProject();
    myMembers = ExtractSuperUtil.possibleMembersToExtract(sourceClass);
    setTitle(ScalaBundle.message("extract.trait.title"));
    init();
    setupDialog();
    updateOkStatus();
  }

  public List<ScalaExtractMemberInfo> getSelectedMembers() {
    ArrayList<ScalaExtractMemberInfo> result = new ArrayList<ScalaExtractMemberInfo>();
    for (ScalaExtractMemberInfo info: myMembers) {
      if (info.isChecked()) {
        result.add(info);
      }
    }
    return result;
  }

  public String getTraitName() {
    return traitNameFld.getText();
  }

  public String getPackageName() {
    return myPackageNameField.getText();
  }

  private void setupDialog() {
    setOKButtonText("Refactor");
    setOKButtonMnemonic('R');
    sourceNameLbl.setText(ScalaBundle.message("extract.trait.top.label.text"));
    traitNameLbl.setText(ScalaBundle.message("extract.trait.name"));
    packageLbl.setText(ScalaBundle.message("extract.trait.package.label"));
    mySourceClassFld.setText(ExtractSuperUtil.classPresentableName(mySourceClass));

    memberSelectionPane.add(createMemberSelectionPanel(), new GridConstraints());
    myPackageNameField = createPackageNameField();
    packageSelectionPane.add(myPackageNameField, BorderLayout.CENTER);

    traitNameFld.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateOkStatus();
      }
    });
  }

  private JPanel createMemberSelectionPanel() {
    String title = ScalaBundle.message("members.to.extract");
    String abstractHeader = ScalaBundle.message("extract.abstracts");
    return new ScalaExtractMembersSelectionPanel(title, myMembers, abstractHeader);
  }

  protected PackageNameReferenceEditorCombo createPackageNameField() {
    String name = ExtractSuperUtil.packageName(mySourceClass);
    return new PackageNameReferenceEditorCombo(name, myProject, DESTINATION_PACKAGE_RECENT_KEY,
        RefactoringBundle.message("choose.destination.package"));
  }

  private void updateOkStatus() {
    setOKActionEnabled(ScalaNamesUtil.isIdentifier(getTraitName()));
  }

  @Override
  protected void doOKAction() {
    String pckgWarning = ExtractSuperUtil.checkPackage(getPackageName(), getTraitName(), mySourceClass);
    if (pckgWarning != null) {
      showErrorMessage(pckgWarning);
      return;
    }

    super.doOKAction();
  }

  private void showErrorMessage(String message) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, myProject);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  protected JComponent createContentPane() {
    return contentPane;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return traitNameFld;
  }
}
