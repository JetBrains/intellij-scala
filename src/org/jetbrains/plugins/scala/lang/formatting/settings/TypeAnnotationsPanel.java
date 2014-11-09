package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

/**
 * Pavel Fatin
 */
public class TypeAnnotationsPanel extends CodeStyleAbstractPanel {
    public static final String CONFIGURABLE_ID = "com.intellij.profile.codeInspection.ui.ProjectInspectionToolsConfigurableProvider";

    private JPanel contentPanel;
    private JComboBox myPublicPropertyComboBox;
    private JComboBox myProtectedPropertyComboBox;
    private JComboBox myPrivatePropertyComboBox;
    private JComboBox myPublicMethodComboBox;
    private JComboBox myProtectedMethodComboBox;
    private JComboBox myPrivateMethodComboBox;
    private JComboBox myOverridingPropertyComboBox;
    private JComboBox myOverridingMethodComboBox;
    private JComboBox myLocalPropertyComboBox;
    private JComboBox myLocalMethodComboBox;
    private JComboBox mySimplePropertyComboBox;
    private JComboBox mySimpleMethodComboBox;
    private JPanel myLinkContainer;

    protected TypeAnnotationsPanel(@NotNull CodeStyleSettings settings) {
        super(settings);

        myLocalPropertyComboBox.setModel(new EnumComboBoxModel<TypeAnnotationRequirement>(TypeAnnotationRequirement.class));
        myPublicPropertyComboBox.setModel(new EnumComboBoxModel<TypeAnnotationRequirement>(TypeAnnotationRequirement.class));
        myProtectedPropertyComboBox.setModel(new EnumComboBoxModel<TypeAnnotationRequirement>(TypeAnnotationRequirement.class));
        myPrivatePropertyComboBox.setModel(new EnumComboBoxModel<TypeAnnotationRequirement>(TypeAnnotationRequirement.class));
        myOverridingPropertyComboBox.setModel(new EnumComboBoxModel<TypeAnnotationPolicy>(TypeAnnotationPolicy.class));
        mySimplePropertyComboBox.setModel(new EnumComboBoxModel<TypeAnnotationPolicy>(TypeAnnotationPolicy.class));

        myLocalMethodComboBox.setModel(new EnumComboBoxModel<TypeAnnotationRequirement>(TypeAnnotationRequirement.class));
        myPublicMethodComboBox.setModel(new EnumComboBoxModel<TypeAnnotationRequirement>(TypeAnnotationRequirement.class));
        myProtectedMethodComboBox.setModel(new EnumComboBoxModel<TypeAnnotationRequirement>(TypeAnnotationRequirement.class));
        myPrivateMethodComboBox.setModel(new EnumComboBoxModel<TypeAnnotationRequirement>(TypeAnnotationRequirement.class));
        myOverridingMethodComboBox.setModel(new EnumComboBoxModel<TypeAnnotationPolicy>(TypeAnnotationPolicy.class));
        mySimpleMethodComboBox.setModel(new EnumComboBoxModel<TypeAnnotationPolicy>(TypeAnnotationPolicy.class));

        HyperlinkLabel link = new HyperlinkLabel("Configure type annotation inspection");
        link.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(final HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    final OptionsEditor optionsEditor =
                            OptionsEditor.KEY.getData(DataManager.getInstance().getDataContextFromFocus().getResultSync());
                    if (optionsEditor != null) {
                        UnnamedConfigurable configurable =
                                optionsEditor.findConfigurableById(CONFIGURABLE_ID);
                        if (configurable instanceof ConfigurableWrapper) {
                            configurable = ((ConfigurableWrapper) configurable).getConfigurable();
                        }
                        if (configurable != null && configurable instanceof ErrorsConfigurable) {
                            final ErrorsConfigurable errorsConfigurable = (ErrorsConfigurable) configurable;
                            optionsEditor.clearSearchAndSelect((Configurable) configurable).doWhenDone(new Runnable() {
                                public void run() {
                                    errorsConfigurable.selectInspectionTool("TypeAnnotation");
                                }
                            });
                        }
                    }
                }
            }
        });
        myLinkContainer.setLayout(new BorderLayout());
        myLinkContainer.add(link);

        resetImpl(settings);
    }

    @Override
    protected String getTabTitle() {
        return "Type Annotations";
    }

    @Override
    protected int getRightMargin() {
        return 0;
    }

    @Nullable
    @Override
    protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
        return new ScalaEditorHighlighter(null, null, scheme);
    }

    @NotNull
    @Override
    protected FileType getFileType() {
        return ScalaFileType.SCALA_FILE_TYPE;
    }

    @Nullable
    @Override
    protected String getPreviewText() {
        return "";
    }

    @Override
    public void apply(CodeStyleSettings settings) {
        ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        scalaSettings.LOCAL_PROPERTY_TYPE_ANNOTATION = myLocalPropertyComboBox.getSelectedIndex();
        scalaSettings.PUBLIC_PROPERTY_TYPE_ANNOTATION = myPublicPropertyComboBox.getSelectedIndex();
        scalaSettings.PROTECTED_PROPERTY_TYPE_ANNOTATION = myProtectedPropertyComboBox.getSelectedIndex();
        scalaSettings.PRIVATE_PROPERTY_TYPE_ANNOTATION = myPrivatePropertyComboBox.getSelectedIndex();
        scalaSettings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = myOverridingPropertyComboBox.getSelectedIndex();
        scalaSettings.SIMPLE_PROPERTY_TYPE_ANNOTATION = mySimplePropertyComboBox.getSelectedIndex();

        scalaSettings.LOCAL_METHOD_TYPE_ANNOTATION = myLocalMethodComboBox.getSelectedIndex();
        scalaSettings.PUBLIC_METHOD_TYPE_ANNOTATION = myPublicMethodComboBox.getSelectedIndex();
        scalaSettings.PROTECTED_METHOD_TYPE_ANNOTATION = myProtectedMethodComboBox.getSelectedIndex();
        scalaSettings.PRIVATE_METHOD_TYPE_ANNOTATION = myPrivateMethodComboBox.getSelectedIndex();
        scalaSettings.OVERRIDING_METHOD_TYPE_ANNOTATION = myOverridingMethodComboBox.getSelectedIndex();
        scalaSettings.SIMPLE_METHOD_TYPE_ANNOTATION = mySimpleMethodComboBox.getSelectedIndex();
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
        ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        return (scalaSettings.LOCAL_PROPERTY_TYPE_ANNOTATION != myLocalPropertyComboBox.getSelectedIndex() ||
                scalaSettings.PUBLIC_PROPERTY_TYPE_ANNOTATION != myPublicPropertyComboBox.getSelectedIndex() ||
                scalaSettings.PROTECTED_PROPERTY_TYPE_ANNOTATION != myProtectedPropertyComboBox.getSelectedIndex() ||
                scalaSettings.PRIVATE_PROPERTY_TYPE_ANNOTATION != myPrivatePropertyComboBox.getSelectedIndex() ||
                scalaSettings.OVERRIDING_PROPERTY_TYPE_ANNOTATION != myOverridingPropertyComboBox.getSelectedIndex() ||
                scalaSettings.SIMPLE_PROPERTY_TYPE_ANNOTATION != mySimplePropertyComboBox.getSelectedIndex() ||
                scalaSettings.LOCAL_METHOD_TYPE_ANNOTATION != myLocalMethodComboBox.getSelectedIndex() ||
                scalaSettings.PUBLIC_METHOD_TYPE_ANNOTATION != myPublicMethodComboBox.getSelectedIndex() ||
                scalaSettings.PROTECTED_METHOD_TYPE_ANNOTATION != myProtectedMethodComboBox.getSelectedIndex() ||
                scalaSettings.PRIVATE_METHOD_TYPE_ANNOTATION != myPrivateMethodComboBox.getSelectedIndex() ||
                scalaSettings.OVERRIDING_METHOD_TYPE_ANNOTATION != myOverridingMethodComboBox.getSelectedIndex() ||
                scalaSettings.SIMPLE_METHOD_TYPE_ANNOTATION != mySimpleMethodComboBox.getSelectedIndex());
    }

    @Nullable
    @Override
    public JComponent getPanel() {
        return contentPanel;
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
        ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        myLocalPropertyComboBox.setSelectedIndex(scalaSettings.LOCAL_PROPERTY_TYPE_ANNOTATION);
        myPublicPropertyComboBox.setSelectedIndex(scalaSettings.PUBLIC_PROPERTY_TYPE_ANNOTATION);
        myProtectedPropertyComboBox.setSelectedIndex(scalaSettings.PROTECTED_PROPERTY_TYPE_ANNOTATION);
        myPrivatePropertyComboBox.setSelectedIndex(scalaSettings.PRIVATE_PROPERTY_TYPE_ANNOTATION);
        myOverridingPropertyComboBox.setSelectedIndex(scalaSettings.OVERRIDING_PROPERTY_TYPE_ANNOTATION);
        mySimplePropertyComboBox.setSelectedIndex(scalaSettings.SIMPLE_PROPERTY_TYPE_ANNOTATION);

        myLocalMethodComboBox.setSelectedIndex(scalaSettings.LOCAL_METHOD_TYPE_ANNOTATION);
        myPublicMethodComboBox.setSelectedIndex(scalaSettings.PUBLIC_METHOD_TYPE_ANNOTATION);
        myProtectedMethodComboBox.setSelectedIndex(scalaSettings.PROTECTED_METHOD_TYPE_ANNOTATION);
        myPrivateMethodComboBox.setSelectedIndex(scalaSettings.PRIVATE_METHOD_TYPE_ANNOTATION);
        myOverridingMethodComboBox.setSelectedIndex(scalaSettings.OVERRIDING_METHOD_TYPE_ANNOTATION);
        mySimpleMethodComboBox.setSelectedIndex(scalaSettings.SIMPLE_METHOD_TYPE_ANNOTATION);
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
        contentPanel = new JPanel();
        contentPanel.setLayout(new GridLayoutManager(17, 4, new Insets(0, 0, 0, 0), -1, -1));
        final Spacer spacer1 = new Spacer();
        contentPanel.add(spacer1, new GridConstraints(16, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        contentPanel.add(spacer2, new GridConstraints(16, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Value");
        contentPanel.add(label1, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Method");
        contentPanel.add(label2, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText("Instance Type Annotations");
        contentPanel.add(titledSeparator1, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myPublicPropertyComboBox = new JComboBox();
        contentPanel.add(myPublicPropertyComboBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Public:");
        contentPanel.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Protected:");
        contentPanel.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Private:");
        contentPanel.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myProtectedPropertyComboBox = new JComboBox();
        contentPanel.add(myProtectedPropertyComboBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myPrivatePropertyComboBox = new JComboBox();
        contentPanel.add(myPrivatePropertyComboBox, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myPublicMethodComboBox = new JComboBox();
        contentPanel.add(myPublicMethodComboBox, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myProtectedMethodComboBox = new JComboBox();
        contentPanel.add(myProtectedMethodComboBox, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myPrivateMethodComboBox = new JComboBox();
        contentPanel.add(myPrivateMethodComboBox, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final TitledSeparator titledSeparator2 = new TitledSeparator();
        titledSeparator2.setText("Special Cases");
        contentPanel.add(titledSeparator2, new GridConstraints(10, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Value");
        contentPanel.add(label6, new GridConstraints(11, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("Method");
        contentPanel.add(label7, new GridConstraints(11, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myOverridingPropertyComboBox = new JComboBox();
        contentPanel.add(myOverridingPropertyComboBox, new GridConstraints(12, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myOverridingMethodComboBox = new JComboBox();
        contentPanel.add(myOverridingMethodComboBox, new GridConstraints(12, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final TitledSeparator titledSeparator3 = new TitledSeparator();
        titledSeparator3.setText("Local Type Annotations");
        contentPanel.add(titledSeparator3, new GridConstraints(6, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("Value");
        contentPanel.add(label8, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("Method");
        contentPanel.add(label9, new GridConstraints(7, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myLocalPropertyComboBox = new JComboBox();
        contentPanel.add(myLocalPropertyComboBox, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myLocalMethodComboBox = new JComboBox();
        contentPanel.add(myLocalMethodComboBox, new GridConstraints(8, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        label10.setText("Overriding:");
        contentPanel.add(label10, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        label11.setText("Local:");
        contentPanel.add(label11, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label12 = new JLabel();
        label12.setText("Simple:");
        contentPanel.add(label12, new GridConstraints(13, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        mySimplePropertyComboBox = new JComboBox();
        contentPanel.add(mySimplePropertyComboBox, new GridConstraints(13, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        mySimpleMethodComboBox = new JComboBox();
        contentPanel.add(mySimpleMethodComboBox, new GridConstraints(13, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label13 = new JLabel();
        label13.setText("<html><body>\n<br>\n<strong>Legend:</strong>\n<ul>\n<li>Add - insert in generated code.</li>\n<li>Check - warn when not present.</li>\n</ul>\n</body></html>");
        contentPanel.add(label13, new GridConstraints(14, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        contentPanel.add(spacer3, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 10), null, new Dimension(-1, 10), 0, false));
        final Spacer spacer4 = new Spacer();
        contentPanel.add(spacer4, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 10), null, new Dimension(-1, 10), 0, false));
        myLinkContainer = new JPanel();
        myLinkContainer.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPanel.add(myLinkContainer, new GridConstraints(15, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPanel;
    }
}
