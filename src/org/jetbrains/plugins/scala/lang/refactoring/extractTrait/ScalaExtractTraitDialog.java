package org.jetbrains.plugins.scala.lang.refactoring.extractTrait;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
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
        for (ScalaExtractMemberInfo info : myMembers) {
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

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        mySourceClassFld = new JTextField();
        mySourceClassFld.setEditable(false);
        mySourceClassFld.setEnabled(true);
        panel1.add(mySourceClassFld, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        sourceNameLbl = new JLabel();
        sourceNameLbl.setText("Extract trait from:");
        panel1.add(sourceNameLbl, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        traitNameLbl = new JLabel();
        traitNameLbl.setText("Trait name:");
        panel1.add(traitNameLbl, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        traitNameFld = new JTextField();
        panel1.add(traitNameFld, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer1 = new Spacer();
        contentPane.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        memberSelectionPane = new JPanel();
        memberSelectionPane.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(memberSelectionPane, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        packageSelectionPane = new JPanel();
        packageSelectionPane.setLayout(new BorderLayout(0, 0));
        panel2.add(packageSelectionPane, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        packageLbl = new JLabel();
        packageLbl.setText("Package for new trait:");
        panel2.add(packageLbl, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
