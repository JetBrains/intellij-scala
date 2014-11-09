package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.util.*;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.*;
import java.util.EventListener;
import java.util.HashMap;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaIntroduceParameterDialog extends RefactoringDialog implements NamedDialog {
    private JPanel contentPane;
    private JComboBox typeComboBox;
    private ComboBox myNameComboBox;
    private JCheckBox makeWithDefaultValueCheckBox;
    private JCheckBox replaceAllOccurrencesCheckBox;
    private JLabel myNameLabel;
    private JLabel myTypeLabel;
    private JButton buttonOK;
    private Project project;
    private ScType[] myTypes;
    private int occurrencesCount;
    private ScalaVariableValidator validator;
    private String[] possibleNames;
    private EventListenerList myListenerList = new EventListenerList();

    private static final String REFACTORING_NAME = ScalaBundle.message("introduce.parameter.title");
    private HashMap<String, ScType> myTypeMap;
    private ItemListener listener;
    private DocumentListener documentListener;
    private PsiMethod methodToSearchFor;
    private TextRange[] occurrences;
    private int startOffset;
    private int endOffset;
    private ScFunctionDefinition function;
    private Editor editor;
    private ScExpression expression;

    protected ScalaIntroduceParameterDialog(Project project,
                                            Editor editor,
                                            ScType[] myTypes,
                                            TextRange[] occurrences,
                                            ScalaVariableValidator validator,
                                            String[] possibleNames, PsiMethod methodToSearchFor,
                                            int startOffset, int endOffset,
                                            ScFunctionDefinition function,
                                            ScExpression expression) {
        super(project, true);
        this.project = project;
        this.myTypes = myTypes;
        this.occurrencesCount = occurrences.length;
        this.occurrences = occurrences;
        this.validator = validator;
        this.possibleNames = possibleNames;
        this.methodToSearchFor = methodToSearchFor;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.function = function;
        this.editor = editor;
        this.expression = expression;

        setTitle(REFACTORING_NAME);
        init();
        setUpNameComboBox(possibleNames);
        setUpDialog();
        updateOkStatus();
    }

    @Override
    protected JComponent createNorthPanel() {
        return contentPane;
    }

    @Override
    protected JComponent createCenterPanel() {
        return null;
    }

    @Override
    protected void dispose() {
        myNameComboBox.removeItemListener(listener);
        ((EditorTextField) myNameComboBox.getEditor().getEditorComponent()).removeDocumentListener(documentListener);
        super.dispose();
    }

    private void setUpNameComboBox(String[] possibleNames) {

        final EditorComboBoxEditor comboEditor = new StringComboboxEditor(project, ScalaFileType.SCALA_FILE_TYPE, myNameComboBox);

        myNameComboBox.setEditor(comboEditor);
        myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

        myNameComboBox.setEditable(true);
        myNameComboBox.setMaximumRowCount(8);
        myListenerList.add(DataChangedListener.class, new DataChangedListener());

        listener = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                fireNameDataChanged();
            }
        };
        myNameComboBox.addItemListener(
                listener
        );

        documentListener = new DocumentListener() {
            public void beforeDocumentChange(DocumentEvent event) {
            }

            public void documentChanged(DocumentEvent event) {
                fireNameDataChanged();
            }
        };
        ((EditorTextField) myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(documentListener);

        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                myNameComboBox.requestFocus();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

        for (String possibleName : possibleNames) {
            myNameComboBox.addItem(possibleName);
        }
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
        final Spacer spacer1 = new Spacer();
        contentPane.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        makeWithDefaultValueCheckBox = new JCheckBox();
        makeWithDefaultValueCheckBox.setText("Make with default value");
        makeWithDefaultValueCheckBox.setMnemonic('D');
        makeWithDefaultValueCheckBox.setDisplayedMnemonicIndex(10);
        contentPane.add(makeWithDefaultValueCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        replaceAllOccurrencesCheckBox = new JCheckBox();
        replaceAllOccurrencesCheckBox.setText("Replace all occurrences");
        replaceAllOccurrencesCheckBox.setMnemonic('A');
        replaceAllOccurrencesCheckBox.setDisplayedMnemonicIndex(8);
        contentPane.add(replaceAllOccurrencesCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myTypeLabel = new JLabel();
        myTypeLabel.setText("Parameter of type:");
        myTypeLabel.setDisplayedMnemonic('T');
        myTypeLabel.setDisplayedMnemonicIndex(13);
        panel1.add(myTypeLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myNameLabel = new JLabel();
        myNameLabel.setText("Name:");
        myNameLabel.setDisplayedMnemonic('N');
        myNameLabel.setDisplayedMnemonicIndex(0);
        panel1.add(myNameLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeComboBox = new JComboBox();
        panel1.add(typeComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myNameComboBox = new ComboBox();
        myNameComboBox.setEditable(true);
        panel1.add(myNameComboBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

    class DataChangedListener implements EventListener {
        void dataChanged() {
            updateOkStatus();
        }
    }

    @Nullable
    public String getEnteredName() {
        if (myNameComboBox.getEditor().getItem() instanceof String &&
                ((String) myNameComboBox.getEditor().getItem()).length() > 0) {
            return (String) myNameComboBox.getEditor().getItem();
        } else {
            return null;
        }
    }

    private void updateOkStatus() {
        String text = getEnteredName();
        boolean enabled = ScalaNamesUtil.isIdentifier(text);
        if (!enabled) {
            String errorText = text;
            if (errorText == null) errorText = "\"\"";
            errorText += " is not valid identifier";
            setErrorText(errorText);
        } else setErrorText(null);
        getPreviewAction().setEnabled(enabled);
        getRefactorAction().setEnabled(enabled);
    }

    private void fireNameDataChanged() {
        Object[] list = myListenerList.getListenerList();
        for (Object aList : list) {
            if (aList instanceof DataChangedListener) {
                ((DataChangedListener) aList).dataChanged();
            }
        }
    }

    public JComponent getPreferredFocusedComponent() {
        return myNameComboBox;
    }

    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_PARAMETER);
    }

    private void setUpDialog() {
        replaceAllOccurrencesCheckBox.setMnemonic(KeyEvent.VK_A);
        replaceAllOccurrencesCheckBox.setFocusable(false);
        makeWithDefaultValueCheckBox.setMnemonic(KeyEvent.VK_D);
        makeWithDefaultValueCheckBox.setFocusable(false);
        myNameLabel.setLabelFor(myNameComboBox);
        myTypeLabel.setLabelFor(typeComboBox);

        boolean nullText = false;
        for (ScType myType : myTypes) {
            if (ScTypeUtil.presentableText(myType) == null) nullText = true;
        }

        // Type specification
        if (myTypes == null || nullText) {
            typeComboBox.setEnabled(false);
        } else {
            myTypeMap = ScalaRefactoringUtil.getCompatibleTypeNames(myTypes);
            for (String typeName : myTypeMap.keySet()) {
                typeComboBox.addItem(typeName);
            }
        }

        if (methodToSearchFor instanceof ScFunction) {
            makeWithDefaultValueCheckBox.setSelected(ScalaApplicationSettings.getInstance().INTRODUCE_PARAMETER_CREATE_DEFAULT);
            makeWithDefaultValueCheckBox.setEnabled(true);
        } else {
            makeWithDefaultValueCheckBox.setSelected(false);
            makeWithDefaultValueCheckBox.setEnabled(false);
            makeWithDefaultValueCheckBox.setVisible(false);
        }

        // Replace occurences
        if (occurrencesCount > 1) {
            replaceAllOccurrencesCheckBox.setSelected(false);
            replaceAllOccurrencesCheckBox.setEnabled(true);
            replaceAllOccurrencesCheckBox.setText(replaceAllOccurrencesCheckBox.getText() +
                    " (" + occurrencesCount + " occurrences)");
        } else {
            replaceAllOccurrencesCheckBox.setSelected(false);
            replaceAllOccurrencesCheckBox.setEnabled(false);
            replaceAllOccurrencesCheckBox.setVisible(false);
        }

    }

    public ScType getSelectedType() {
        if (myTypeMap.size() == 0) return null;
        return myTypeMap.get(typeComboBox.getSelectedItem());
    }

    public boolean isReplaceAllOccurrences() {
        return replaceAllOccurrencesCheckBox.isSelected();
    }

    public boolean isDeclareDefault() {
        return makeWithDefaultValueCheckBox.isSelected();
    }

    @Override
    protected void doAction() {
        if (!validator.isOK(this)) {
            return;
        }
        if (makeWithDefaultValueCheckBox.isEnabled()) {
            ScalaApplicationSettings.getInstance().INTRODUCE_PARAMETER_CREATE_DEFAULT = makeWithDefaultValueCheckBox.isSelected();
        }
        ScalaIntroduceParameterProcessor scalaIntroduceParameterProcessor =
                new ScalaIntroduceParameterProcessor(project, editor, methodToSearchFor, function,
                        isReplaceAllOccurrences(), occurrences, startOffset, endOffset, getEnteredName(), isDeclareDefault(),
                        getSelectedType(), expression);
        invokeRefactoring(scalaIntroduceParameterProcessor);
    }
}
