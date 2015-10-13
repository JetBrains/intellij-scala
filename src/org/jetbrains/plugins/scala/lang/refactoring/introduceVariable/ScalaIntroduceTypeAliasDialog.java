package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.lang.refactoring.util.*;
import scala.Tuple2;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.*;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;


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
    private JCheckBox myReplaceInInheritors;
    private JButton buttonOK;
    private JButton buttonCancel;

    private Project project;
    private ScTypeElement myTypeElement;
    private EventListenerList myListenerList = new EventListenerList();
    private ConflictsReporter conflictsReporter;
    private Editor editor;
    private ScopeItem currentScope;
    private Map<ScopeItem, Tuple2<ScTypeElement[], ScalaTypeValidator[]>> inheritanceDataMap;

    private static final String REFACTORING_NAME = ScalaBundle.message("introduce.type.alias.title");

    public ScalaIntroduceTypeAliasDialog(Project project,
                                         ScTypeElement myTypeElement,
                                         ScopeItem[] possibleScopes,
                                         ScopeItem mainScope,
                                         ConflictsReporter conflictReporter,
                                         Editor editor) {
        super(project, true);
        this.project = project;
        this.myTypeElement = myTypeElement;
        this.currentScope = mainScope;
        this.conflictsReporter = conflictReporter;
        this.editor = editor;
        this.inheritanceDataMap = new HashMap<ScopeItem, Tuple2<ScTypeElement[], ScalaTypeValidator[]>>();

        setUpNameComboBox(currentScope.getAvailableNames());
        setUpScopeComboBox(possibleScopes, mainScope);

        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        setTitle(REFACTORING_NAME);
        init();
        setUpDialog(currentScope);
        updateOkStatus();
    }

    protected void doOKAction() {
        if (currentScope instanceof SimpleScopeItem) {
            if (!handleInheritedClasses((SimpleScopeItem) currentScope)) {
                return;
            }
        }
        ScopeItem newScope = currentScope;

        ScalaValidator validator = null;
        if (currentScope instanceof PackageScopeItem) {
            PackageScopeItem packageScopeItem = (PackageScopeItem) currentScope;
            currentScope = ScopeSuggester.handleOnePackage(myTypeElement, packageScopeItem.getName(),
                    (PsiDirectory) packageScopeItem.fileEncloser(), conflictsReporter, myTypeElement.getProject(), editor,
                    isReplaceAllOccurrences(), getEnteredName());

            validator = packageScopeItem.validator();
        } else if (currentScope instanceof SimpleScopeItem) {
            validator = ((SimpleScopeItem) currentScope).typeValidator();
        }

        if ((validator != null) && (!validator.isOK(this))) {
            currentScope = newScope;
            return;
        }

        super.doOKAction();
    }

    public JComponent getPreferredFocusedComponent() {
        return myNameComboBox;
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

    private void setUpOccurrences(ScopeItem item) {
        setUpCompanionObjOcc(item);
        setUpReplaceInInheritors(item);

        myCbReplaceAllOccurences.setSelected(false);
        if (item instanceof PackageScopeItem) {
            myCbReplaceAllOccurences.setEnabled(true);
            myCbReplaceAllOccurences.setText("Replace all occurrences");
        } else if (item instanceof SimpleScopeItem) {
            SimpleScopeItem simpleScopeItem = (SimpleScopeItem) item;
            int occurrencesCount = simpleScopeItem.usualOccurrences().length;
            if (occurrencesCount > 1) {
                myCbReplaceAllOccurences.setEnabled(true);
                myCbReplaceAllOccurences.setText("Replace all occurrences" + occurrencesCount);
            } else {
                myCbReplaceAllOccurences.setEnabled(false);
            }
        }
    }

    private void setUpCompanionObjOcc(ScopeItem item) {
        myReplaceCompanionObjectOcc.setSelected(false);
        if (item instanceof SimpleScopeItem) {
            SimpleScopeItem simpleScopeItem = (SimpleScopeItem) item;
            int companionObjOccCount = simpleScopeItem.occurrencesInCompanion().length;
            if (companionObjOccCount > 0) {
                myReplaceCompanionObjectOcc.setEnabled(true);
                myReplaceCompanionObjectOcc.setText("Replace occurrences available from companion object (" + companionObjOccCount + " occurrences)");
            } else {
                myReplaceCompanionObjectOcc.setEnabled(false);
            }
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
        panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myCbReplaceAllOccurences = new JCheckBox();
        myCbReplaceAllOccurences.setText("Replace all occurrences");
        myCbReplaceAllOccurences.setMnemonic('A');
        myCbReplaceAllOccurences.setDisplayedMnemonicIndex(8);
        panel1.add(myCbReplaceAllOccurences, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myReplaceCompanionObjectOcc = new JCheckBox();
        myReplaceCompanionObjectOcc.setText("Replace occurrences available from companion object");
        panel1.add(myReplaceCompanionObjectOcc, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myReplaceInInheritors = new JCheckBox();
        myReplaceInInheritors.setText("Replace occurences in class inheritors");
        panel1.add(myReplaceInInheritors, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
                currentScope = item;
                setUpOccurrences(item);
                updateNameComboBox(item.getAvailableNames());
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

    private void setUpDialog(ScopeItem scopeItem) {
        myCbReplaceAllOccurences.setMnemonic(KeyEvent.VK_A);
        myCbReplaceAllOccurences.setFocusable(false);
        myNameLabel.setLabelFor(myNameComboBox);
        myTypeLabel.setLabelFor(myTypeTextField);
        myTypeTextField.setText(myTypeElement.calcType().presentableText());
        setUpOccurrences(scopeItem);
    }

    private void setUpScopeComboBox(ScopeItem[] elements, ScopeItem mainScope) {
        myScopeCombobox.addItemListener(new ScopeItemChangeListener());

        for (ScopeItem scope : elements) {
            myScopeCombobox.addItem(scope);
        }

        if (mainScope != null) {
            myScopeCombobox.setSelectedItem(mainScope);
        }
    }

    private void setUpReplaceInInheritors(ScopeItem scope) {
        myReplaceInInheritors.setSelected(false);
        myReplaceInInheritors.setEnabled(false);
        if (scope instanceof SimpleScopeItem) {
            SimpleScopeItem simpleScopeItem = (SimpleScopeItem) scope;
            if (simpleScopeItem.isClass() || simpleScopeItem.isTrait()) {
                myReplaceInInheritors.setEnabled(true);
            } else {
                myReplaceInInheritors.setEnabled(false);
            }
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

    public boolean isReplaceOccurrenceInInheritors() {
        return myReplaceInInheritors.isSelected();
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
        return currentScope;
    }

    //return false if there is conflict in validator
    private boolean handleInheritedClasses(SimpleScopeItem simpleScopeItem) {
        ScTypeDefinition classOrTrait = PsiTreeUtil.getParentOfType(simpleScopeItem.fileEncloser(), ScClass.class, ScTrait.class);

        if (myReplaceInInheritors.isSelected() && (classOrTrait != null)) {
            ScopeItem selectedScope = getSelectedScope();

            if (selectedScope == null) {
                return true;
            }

            Tuple2<ScTypeElement[], ScalaTypeValidator[]> inheritors;
            if (inheritanceDataMap.containsKey(selectedScope)) {
                inheritors = inheritanceDataMap.get(selectedScope);
            } else {
                inheritors = ScalaRefactoringUtil.getOccurrencesInInheritors(myTypeElement, classOrTrait,
                        conflictsReporter, project, editor);
                inheritanceDataMap.put(selectedScope, inheritors);
            }

            simpleScopeItem.setInheretedOccurrences(inheritors._1());

            for (ScalaTypeValidator validator : inheritors._2()) {
                if (!validator.isOK(this)) {
                    return false;
                }
            }

            return true;
        }
        return true;
    }
}
