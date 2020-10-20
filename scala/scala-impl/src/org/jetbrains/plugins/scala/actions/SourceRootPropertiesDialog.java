package org.jetbrains.plugins.scala.actions;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;

import javax.swing.*;

// Copy of com.intellij.openapi.roots.ui.configuration.JavaSourceRootEditHandlerBase.SourceRootPropertiesDialog (which is private)
final class SourceRootPropertiesDialog extends DialogWrapper {
    private final JTextField myPackagePrefixField;
    private final JCheckBox myIsGeneratedCheckBox;
    private final JPanel myMainPanel;
    @NotNull private final JavaSourceRootProperties myProperties;

    SourceRootPropertiesDialog(@NotNull JComponent parentComponent, @NotNull JavaSourceRootProperties properties) {
      super(parentComponent, true);
      myProperties = properties;
      setTitle(ProjectBundle.message("module.paths.edit.properties.title"));
      myPackagePrefixField = new JTextField();
      myIsGeneratedCheckBox = new JCheckBox(ProjectBundle.message("checkbox.for.generated.sources"));
      myMainPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(ProjectBundle.message("label.package.prefix"), myPackagePrefixField)
        .addComponent(myIsGeneratedCheckBox)
        .getPanel();
      myPackagePrefixField.setText(myProperties.getPackagePrefix());
      myPackagePrefixField.setColumns(25);
      myIsGeneratedCheckBox.setSelected(myProperties.isForGeneratedSources());
      init();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myPackagePrefixField;
    }

    @Override
    protected void doOKAction() {
      myProperties.setPackagePrefix(myPackagePrefixField.getText().trim());
      myProperties.setForGeneratedSources(myIsGeneratedCheckBox.isSelected());
      super.doOKAction();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myMainPanel;
    }
  }
