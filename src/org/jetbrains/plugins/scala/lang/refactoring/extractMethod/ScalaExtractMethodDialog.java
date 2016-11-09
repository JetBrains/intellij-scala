package org.jetbrains.plugins.scala.lang.refactoring.extractMethod;

//import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.MethodSignatureComponent;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil;
import scala.Option;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */
public class ScalaExtractMethodDialog extends DialogWrapper {
  private JButton buttonOK;

  private String REFACTORING_NAME = ScalaBundle.message("extract.method.title");
  private JPanel contentPane;
  private JPanel methodNamePanel;
  private EditorTextField editorTextField;
  private JPanel inputParametersPanel;
  private JPanel myPreviewPanel;
  private JPanel myLinkContainer;
  private JCheckBox mySpecifyTypeChb;
  private JComboBox multipleOutputCombobox;
  private JComboBox visibilityComboBox;
  private JTextField visibiltyTextField;
  private JTextField multipleOutputTextField;
  private JLabel visibilityLabel;
  private JLabel multipleOutputLabel;
  private JLabel myNameLabel;

  private boolean isDefaultClassName = true;
  private final MethodSignatureComponent mySignaturePreview;

  private ScalaExtractMethodSettings settings = null;
  private Project myProject;
  private PsiElement[] myElements;
  private Option<ScType> myHasReturn;

  private PsiElement mySibling;

  private VariableInfo[] myInput;
  private VariableInfo[] myOutput;
  private ScalaExtractMethodDialog.ScalaParameterTablePanel parameterTablePanel;
  private boolean myLastReturn;
  private Option<ScType> myLastMeaningful = null;

  private boolean isInitialized = false;

  public ScalaExtractMethodDialog(final Project project, PsiElement[] elements, Option<ScType> hasReturn, boolean lastReturn,
                                  PsiElement sibling, VariableInfo[] input, VariableInfo[] output,
                                  Option<ScType> lastMeaningful) {
    super(project, true);

    myElements = elements;
    myProject = project;
    myHasReturn = hasReturn;
    myLastReturn = lastReturn;
    myInput = input;
    myOutput = output;
    myLastMeaningful = lastMeaningful;
      mySignaturePreview = new MethodSignatureComponent("", project, ScalaFileType.INSTANCE);
    mySignaturePreview.setMinimumSize(new Dimension(500, 70));

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    mySibling = sibling;
    setUpDialog();

    isInitialized = true;

    updateSettings();
    setUpTypeChb();
    setUpHyperLink();
    updateSignature();

    updateOkStatus();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return editorTextField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  protected JComponent createContentPane() {
    return contentPane;
  }

  private void updateOkStatus() {
    setOKActionEnabled(ScalaNamesUtil.isIdentifier(getMethodName()) &&
            (isPublic() || getVisibilityEncloser().equals("") || ScalaNamesUtil.isIdentifier(getVisibilityEncloser())) &&
            (isTuple() || ScalaNamesUtil.isIdentifier(getMultipleOutputEncloser())));
  }

  private String getVisibilityEncloser() {
    return visibiltyTextField.getText();
  }

  private String getMultipleOutputEncloser() {
    return multipleOutputTextField.getText();
  }

  private void setUpDialog() {
    myNameLabel.setLabelFor(editorTextField);
    editorTextField.addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(com.intellij.openapi.editor.event.DocumentEvent event) {

      }

      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
        if (isDefaultClassName) updateClassName(StringUtil.capitalize(editorTextField.getText()));
        updateSignature();
      }
    });

    setupVisibilityPanel();
    setupParametersPanel();
    setupMultipleOutputsPanel();
    setupPreviewPanel();
  }

  private void updateClassName(String newName) {
    if (isDefaultClassName) {
      multipleOutputTextField.setText(newName);
      isDefaultClassName = true;
    }
  }

  private void setupVisibilityPanel() {
    final String[] visibility = {"Private", "Protected", "Public"};
    for (String v : visibility) {
      visibilityComboBox.addItem(v);
    }

    visibilityComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        visibiltyTextField.setEnabled(!isPublic());
        updateSignature();
      }
    });

    visibiltyTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateSignature();
      }
    });

    setVisibilityPanelVisisbility(isVisibilitySectionAvailable());
  }

  private void setVisibilityPanelVisisbility(boolean value) {
    visibilityLabel.setVisible(value);
    visibilityComboBox.setVisible(value);
    visibiltyTextField.setVisible(value);
  }

  private void setupParametersPanel() {
    ArrayList<VariableData> data = new ArrayList<VariableData>();
    for (VariableInfo aMyInput : myInput) {
      VariableData d = ScalaExtractMethodUtils.convertVariableData(aMyInput, myElements);
      if (d != null) data.add(d);
    }
    parameterTablePanel = new ScalaParameterTablePanel(myProject, data.toArray(new VariableData[data.size()]));
    inputParametersPanel.add(parameterTablePanel);
  }

  private boolean isVisibilitySectionAvailable() {
    return mySibling.getParent() instanceof ScTemplateBody;
  }

  private String getVisibility() {
    if (!isVisibilitySectionAvailable()) return "";
    if (isPublic()) return "";

    if (getVisibilityEncloser().equals("")) {
      return visibilityComboBox.getSelectedItem().toString().toLowerCase() + " ";
    }

    return visibilityComboBox.getSelectedItem().toString().toLowerCase() + "[" + getVisibilityEncloser() + "] ";
  }

  private void setMultipleOutputPanelVisibility(boolean value) {
    multipleOutputLabel.setVisible(value);
    multipleOutputCombobox.setVisible(value);
    multipleOutputTextField.setVisible(value);
  }

  protected void setupMultipleOutputsPanel() {
    updateSettings();
    setMultipleOutputPanelVisibility(settings.outputs().length > 1);

    final String[] outputs = {"Tuple", "Inner case class", "Inner class"};
    for (String v : outputs) {
      multipleOutputCombobox.addItem(v);
    }

    multipleOutputCombobox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        multipleOutputTextField.setEnabled(!isTuple());
        updateSignature();
      }
    });

    multipleOutputTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (!multipleOutputCombobox.getSelectedItem().equals(outputs[0])) {
          isDefaultClassName = false;
        }

        updateSignature();
      }
    });

    multipleOutputTextField.setEnabled(!isTuple());
  }

  protected void setupPreviewPanel() {
    myPreviewPanel.setLayout(new BorderLayout());
    myPreviewPanel.add(mySignaturePreview, BorderLayout.CENTER);

    updateSignature();
  }

  protected void updateSignature() {
    if (!isInitialized) return;

    updateOkStatus();
    updateSettings();
    String text = ScalaExtractMethodUtils.previewSignatureText(settings);
    mySignaturePreview.setSignature(text);
  }

  @Override
  protected void doOKAction() {
    updateSettings();
    super.doOKAction();
  }

  private boolean isInnerClass() {
    return multipleOutputCombobox.getSelectedItem() != null
            && multipleOutputCombobox.getSelectedItem().equals("Inner case class");
  }

  private boolean isCaseClass() {
    return multipleOutputCombobox.getSelectedItem() != null
            && multipleOutputCombobox.getSelectedItem().equals("Inner class");
  }

  private boolean isPublic() {
    return visibilityComboBox.getSelectedItem() != null
            && visibilityComboBox.getSelectedItem().equals("Public");
  }

  private boolean isTuple() {
    return multipleOutputCombobox.getSelectedItem() != null
            && multipleOutputCombobox.getSelectedItem().equals("Tuple");
  }

  private void updateSettings() {
    boolean createClass = isInnerClass() || isCaseClass();
    InnerClassSettings ics = new InnerClassSettings(createClass, getClassName(), getReturns(), isCaseClass());
    ScalaApplicationSettings.ReturnTypeLevel addReturnType = mySpecifyTypeChb.isSelected() ?
            ScalaApplicationSettings.ReturnTypeLevel.ADD : ScalaApplicationSettings.ReturnTypeLevel.REMOVE;

    settings = new ScalaExtractMethodSettings(getMethodName(), getParameters(), getReturns(),
            getVisibility(), mySibling, myElements, myHasReturn, addReturnType, myLastReturn, myLastMeaningful, ics);
  }

  private void setUpTypeChb() {
    mySpecifyTypeChb.setSelected(ScalaExtractMethodUtils.addTypeAnnotation(settings, getVisibility()));

    mySpecifyTypeChb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateSignature();
      }
    });

    visibilityComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        mySpecifyTypeChb.setSelected(ScalaExtractMethodUtils.addTypeAnnotation(settings, getVisibility()));
      }
    });
  }

  private void setUpHyperLink() {
    HyperlinkLabel link = TypeAnnotationUtil.createTypeAnnotationsHLink(settings.nextSibling().getProject(), ScalaBundle.message("default.ta.settings"));
    link.setToolTipText(ScalaBundle.message("default.ta.tooltip") + " return type");
    myLinkContainer.add(link);

    link.setToolTipText(ScalaBundle.message("default.ta.tooltip"));
    link.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        mySpecifyTypeChb.setSelected(ScalaExtractMethodUtils.addTypeAnnotation(settings, getVisibility()));
        updateSignature();
      }
    });
  }

  private String getClassName() {
    if (isCaseClass() || isInnerClass()) {
      return getMultipleOutputEncloser();
    }

    return "";
  }

  public ExtractMethodParameter[] getParameters() {
    VariableData[] data = parameterTablePanel.getVariableData();
    ArrayList<ExtractMethodParameter> list = new ArrayList<ExtractMethodParameter>();
    for (VariableData d : data) {
      ScalaVariableData variableData = (ScalaVariableData) d;
      ExtractMethodParameter param = ExtractMethodParameter.from(variableData);
      list.add(param);
    }
    return list.toArray(new ExtractMethodParameter[list.size()]);
  }

  public ExtractMethodOutput[] getReturns() {
    ArrayList<ExtractMethodOutput> list = new ArrayList<ExtractMethodOutput>();
    for (VariableInfo info : myOutput) {
      ScalaVariableData data = ScalaExtractMethodUtils.convertVariableData(info, myElements);
      list.add(ExtractMethodOutput.from(data));
    }
    return list.toArray(new ExtractMethodOutput[list.size()]);
  }

  private String getMethodName() {
    return editorTextField.getText();
  }

  public ScalaExtractMethodSettings getSettings() {
    return settings;
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
    contentPane.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
    methodNamePanel = new JPanel();
    methodNamePanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(methodNamePanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myNameLabel = new JLabel();
    myNameLabel.setText("Name");
    myNameLabel.setDisplayedMnemonic('N');
    myNameLabel.setDisplayedMnemonicIndex(0);
    methodNamePanel.add(myNameLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    visibilityLabel = new JLabel();
    visibilityLabel.setText("Visibility");
    visibilityLabel.setDisplayedMnemonic('V');
    visibilityLabel.setDisplayedMnemonicIndex(0);
    methodNamePanel.add(visibilityLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    visibiltyTextField = new JTextField();
    methodNamePanel.add(visibiltyTextField, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    multipleOutputTextField = new JTextField();
    methodNamePanel.add(multipleOutputTextField, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    visibilityComboBox = new JComboBox();
    methodNamePanel.add(visibilityComboBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    multipleOutputCombobox = new JComboBox();
    methodNamePanel.add(multipleOutputCombobox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    editorTextField = new EditorTextField();
    editorTextField.setText("");
    methodNamePanel.add(editorTextField, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    multipleOutputLabel = new JLabel();
    multipleOutputLabel.setText("Multiple output");
    multipleOutputLabel.setDisplayedMnemonic('M');
    multipleOutputLabel.setDisplayedMnemonicIndex(0);
    methodNamePanel.add(multipleOutputLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    inputParametersPanel = new JPanel();
    inputParametersPanel.setLayout(new BorderLayout(0, 0));
    panel1.add(inputParametersPanel, new GridConstraints(0, 0, 3, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final TitledSeparator titledSeparator1 = new TitledSeparator();
    titledSeparator1.setText("Parameters");
    inputParametersPanel.add(titledSeparator1, BorderLayout.NORTH);
    myPreviewPanel = new JPanel();
    myPreviewPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(myPreviewPanel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final TitledSeparator titledSeparator2 = new TitledSeparator();
    titledSeparator2.setFocusable(true);
    titledSeparator2.setText("Signature preview");
    contentPane.add(titledSeparator2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel2.setAlignmentX(0.0f);
    panel2.setAlignmentY(0.0f);
    contentPane.add(panel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    mySpecifyTypeChb = new JCheckBox();
    mySpecifyTypeChb.setAlignmentY(0.0f);
    mySpecifyTypeChb.setBorderPainted(false);
    mySpecifyTypeChb.setBorderPaintedFlat(false);
    mySpecifyTypeChb.setContentAreaFilled(false);
    mySpecifyTypeChb.setHideActionText(false);
    mySpecifyTypeChb.setHorizontalAlignment(0);
    mySpecifyTypeChb.setHorizontalTextPosition(11);
    mySpecifyTypeChb.setInheritsPopupMenu(false);
    mySpecifyTypeChb.setMargin(new Insets(0, 0, 0, 0));
    mySpecifyTypeChb.setText("Specify return type");
    mySpecifyTypeChb.setMnemonic('T');
    mySpecifyTypeChb.setDisplayedMnemonicIndex(15);
    panel2.add(mySpecifyTypeChb);
    myLinkContainer = new JPanel();
    myLinkContainer.setLayout(new BorderLayout(0, 0));
    myLinkContainer.setAlignmentX(0.0f);
    myLinkContainer.setAlignmentY(0.0f);
    panel2.add(myLinkContainer);
    visibilityLabel.setLabelFor(visibilityComboBox);
    multipleOutputLabel.setLabelFor(multipleOutputCombobox);
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return contentPane;
  }

  private class ScalaParameterTablePanel extends ParameterTablePanel {
    public ScalaParameterTablePanel(Project project, VariableData[] variableData) {
      super(project, variableData);
    }

    protected void updateSignature() {
      ScalaExtractMethodDialog.this.updateSignature();
    }

    protected void doEnterAction() {
    }

    protected void doCancelAction() {
    }
  }
}