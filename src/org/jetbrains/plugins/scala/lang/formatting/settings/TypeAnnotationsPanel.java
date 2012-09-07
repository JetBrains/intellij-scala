package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.labels.LinkLabel;
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
          final OptionsEditor optionsEditor = OptionsEditor.KEY.getData(DataManager.getInstance().getDataContext());
          if (optionsEditor != null) {
            final ErrorsConfigurable errorsConfigurable = optionsEditor.findConfigurable(ErrorsConfigurable.class);
            if (errorsConfigurable != null) {
              optionsEditor.clearSearchAndSelect(errorsConfigurable).doWhenDone(new Runnable() {
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

    scalaSettings.LOCAL_PROPERTY_TYPE_ANNOTATION =  myLocalPropertyComboBox.getSelectedIndex();
    scalaSettings.PUBLIC_PROPERTY_TYPE_ANNOTATION =  myPublicPropertyComboBox.getSelectedIndex();
    scalaSettings.PROTECTED_PROPERTY_TYPE_ANNOTATION =  myProtectedPropertyComboBox.getSelectedIndex();
    scalaSettings.PRIVATE_PROPERTY_TYPE_ANNOTATION =  myPrivatePropertyComboBox.getSelectedIndex();
    scalaSettings.OVERRIDING_PROPERTY_TYPE_ANNOTATION = myOverridingPropertyComboBox.getSelectedIndex();
    scalaSettings.SIMPLE_PROPERTY_TYPE_ANNOTATION = mySimplePropertyComboBox.getSelectedIndex();

    scalaSettings.LOCAL_METHOD_TYPE_ANNOTATION =  myLocalMethodComboBox.getSelectedIndex();
    scalaSettings.PUBLIC_METHOD_TYPE_ANNOTATION =  myPublicMethodComboBox.getSelectedIndex();
    scalaSettings.PROTECTED_METHOD_TYPE_ANNOTATION =  myProtectedMethodComboBox.getSelectedIndex();
    scalaSettings.PRIVATE_METHOD_TYPE_ANNOTATION =  myPrivateMethodComboBox.getSelectedIndex();
    scalaSettings.OVERRIDING_METHOD_TYPE_ANNOTATION = myOverridingMethodComboBox.getSelectedIndex();
    scalaSettings.SIMPLE_METHOD_TYPE_ANNOTATION = mySimpleMethodComboBox.getSelectedIndex();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    return  (scalaSettings.LOCAL_PROPERTY_TYPE_ANNOTATION != myLocalPropertyComboBox.getSelectedIndex() ||
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
}
