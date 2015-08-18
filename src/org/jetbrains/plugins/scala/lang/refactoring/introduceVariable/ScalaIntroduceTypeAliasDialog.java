package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.scopeSuggester.ScopeItem;
import org.jetbrains.plugins.scala.lang.refactoring.util.*;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.LinkedHashMap;

public class ScalaIntroduceTypeAliasDialog extends DialogWrapper implements NamedDialog {
    private JPanel contentPane;
    private JLabel myTypeLabel;
    private ComboBox myNameComboBox;
    private JLabel myScopeLabel;
    private JComboBox myScopeCombobox;
    private JLabel myNameLabel;
    private JCheckBox myCbReplaceAllOccurences;
    private JTextField myTypeTextField;
    private JCheckBox myReplaceCompanionObjectOcc;
    private JButton buttonOK;
    private JButton buttonCancel;

    private Project project;
    private ScType myType;
    private int occurrencesCount;
    private int companionObjOccCount;
    private ScalaValidator validator;
    private String[] possibleNames;
    private ArrayList<ScopeItem> possibleScopes;
    private LinkedHashMap<String, ScType> myTypeMap = null;
    private EventListenerList myListenerList = new EventListenerList();

    private static final String REFACTORING_NAME = ScalaBundle.message("introduce.type.alias.title");

    public ScalaIntroduceTypeAliasDialog(Project project,
                                         ScType myType,
                                         ArrayList<ScopeItem> possibleScopes) {
        super(project, true);
        this.project = project;
        this.myType = myType;
        this.occurrencesCount = possibleScopes.get(0).occurrences().length;
        this.companionObjOccCount = possibleScopes.get(0).occInCompanionObj().length;
        this.validator = possibleScopes.get(0).validator();
        this.possibleNames = possibleScopes.get(0).possibleNames();
        this.possibleScopes = possibleScopes;
        setUpNameComboBox(possibleNames);
        setUpScopeComboBox(possibleScopes);

        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        setTitle(REFACTORING_NAME);
        init();
        setUpDialog();
        updateOkStatus();
    }

    private void setUpNameComboBox(String[] possibleNames) {

        final EditorComboBoxEditor comboEditor = new StringComboboxEditor(project, ScalaFileType.SCALA_FILE_TYPE, myNameComboBox);

        myNameComboBox.setEditor(comboEditor);
        myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

        myNameComboBox.setEditable(true);
        myNameComboBox.setMaximumRowCount(8);
        myListenerList.add(DataChangedListener.class, new DataChangedListener());

        myNameComboBox.addItemListener(
                new ItemListener() {
                    public void itemStateChanged(ItemEvent e) {
                        fireNameDataChanged();
                    }
                }
        );

        ((EditorTextField) myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
                                                                                                    public void beforeDocumentChange(DocumentEvent event) {
                                                                                                    }

                                                                                                    public void documentChanged(DocumentEvent event) {
                                                                                                        fireNameDataChanged();
                                                                                                    }
                                                                                                }
        );

        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                myNameComboBox.requestFocus();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

        for (String possibleName : possibleNames) {
            myNameComboBox.addItem(possibleName);
        }
    }

    private void updateNameComboBox(String[] possibleNames) {
        myNameComboBox.removeAllItems();
        for (String possibleName : possibleNames) {
            myNameComboBox.addItem(possibleName);
        }
    }

    private void setUpOccurrences() {
        myCbReplaceAllOccurences.setSelected(false);
        if (occurrencesCount > 1) {
            myCbReplaceAllOccurences.setEnabled(true);
            myCbReplaceAllOccurences.setText("Replace all occurrences (" + occurrencesCount + " occurrences)");
        } else {
            myCbReplaceAllOccurences.setEnabled(false);
        }
    }

    private void setUpCompanionObjOcc() {
        myReplaceCompanionObjectOcc.setSelected(false);
        if (companionObjOccCount > 0) {
            myReplaceCompanionObjectOcc.setEnabled(true);
            myReplaceCompanionObjectOcc.setText("Replace occurrences available from companion object (" + companionObjOccCount + " occurrences)");
        } else {
            myReplaceCompanionObjectOcc.setEnabled(false);
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
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myCbReplaceAllOccurences = new JCheckBox();
        myCbReplaceAllOccurences.setText("Replace all occurrences");
        myCbReplaceAllOccurences.setMnemonic('A');
        myCbReplaceAllOccurences.setDisplayedMnemonicIndex(8);
        panel1.add(myCbReplaceAllOccurences, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myReplaceCompanionObjectOcc = new JCheckBox();
        myReplaceCompanionObjectOcc.setText("Replace occurrences available from companion object");
        panel1.add(myReplaceCompanionObjectOcc, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myTypeLabel = new JLabel();
        myTypeLabel.setText("Type:");
        panel2.add(myTypeLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myNameComboBox = new ComboBox();
        myNameComboBox.setEditable(true);
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        myNameComboBox.setModel(defaultComboBoxModel1);
        panel2.add(myNameComboBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myScopeLabel = new JLabel();
        myScopeLabel.setText("Scope ");
        myScopeLabel.setVisible(true);
        panel2.add(myScopeLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myNameLabel = new JLabel();
        myNameLabel.setText("Name:");
        panel2.add(myNameLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myScopeCombobox = new JComboBox();
        myScopeCombobox.setEditable(false);
        final DefaultComboBoxModel defaultComboBoxModel2 = new DefaultComboBoxModel();
        myScopeCombobox.setModel(defaultComboBoxModel2);
        myScopeCombobox.setVisible(true);
        panel2.add(myScopeCombobox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myTypeTextField = new JTextField();
        myTypeTextField.setEnabled(false);
        panel2.add(myTypeTextField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

    class ScopeItemChangeListener implements ItemListener {
        @Override
        public void itemStateChanged(ItemEvent event) {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                ScopeItem item = (ScopeItem) event.getItem();
                PsiElement[] occurrences = item.occurrences();
                occurrencesCount = occurrences.length;
                companionObjOccCount = item.occInCompanionObj().length;

                setUpCompanionObjOcc();
                setUpOccurrences();
                validator = item.validator();
                possibleNames = item.possibleNames();
                updateNameComboBox(possibleNames);
            }
        }
    }

    class DataChangedListener implements EventListener {
        void dataChanged() {
            updateOkStatus();
        }
    }

    private void updateOkStatus() {
        String text = getEnteredName();
        setOKActionEnabled(ScalaNamesUtil.isIdentifier(text));
    }

    private void fireNameDataChanged() {
        Object[] list = myListenerList.getListenerList();
        for (Object aList : list) {
            if (aList instanceof DataChangedListener) {
                ((DataChangedListener) aList).dataChanged();
            }
        }
    }

    private void setUpDialog() {
        myCbReplaceAllOccurences.setMnemonic(KeyEvent.VK_A);
        myCbReplaceAllOccurences.setFocusable(false);
        myNameLabel.setLabelFor(myNameComboBox);
        myTypeLabel.setLabelFor(myTypeTextField);
        myTypeTextField.setText(myType.presentableText());

        // Replace occurences
        setUpCompanionObjOcc();
        setUpOccurrences();
    }

    private void setUpScopeComboBox(ArrayList<ScopeItem> elements) {
        myScopeCombobox.addItemListener(new ScopeItemChangeListener());

        for (ScopeItem scope : elements) {
            myScopeCombobox.addItem(scope);
        }
    }

    @Nullable
    protected JComponent createCenterPanel() {
        return contentPane;
    }

    public boolean isReplaceAllOccurrences() {
        return myCbReplaceAllOccurences.isSelected();
    }

    public boolean isReplaceOccurrenceIncompanionObject() {
        return myReplaceCompanionObjectOcc.isSelected();
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

    public ScopeItem getSelectedScope() {
        if (myScopeCombobox.getSelectedItem() instanceof ScopeItem) {
            return (ScopeItem) myScopeCombobox.getSelectedItem();
        }

        return null;
    }
}
