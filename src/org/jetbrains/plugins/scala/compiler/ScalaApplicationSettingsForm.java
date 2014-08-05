package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.containers.ComparatorUtil;
import org.jdesktop.swingx.combobox.ListComboBoxModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugin.scala.compiler.CompileOrder;
import org.jetbrains.plugin.scala.compiler.IncrementalType;
import org.jetbrains.plugin.scala.compiler.NameHashing;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class ScalaApplicationSettingsForm implements Configurable {
  private JPanel myCompilationServerPanel;
  private RawCommandLineEditor myCompilationServerJvmParameters;
  private JTextField myCompilationServerPort;
  private JTextField myCompilationServerMaximumHeapSize;
  private JCheckBox myEnableCompileServer;
  private JPanel myContentPanel;
  private JdkComboBox myCompilationServerSdk;
  private MultiLineLabel myNote;
  private JPanel mySdkPanel;
  private JCheckBox showTypeInfoOnCheckBox;
  private JSpinner delaySpinner;
  private JComboBox<IncrementalType> myIncrementalTypeCmb;
  private JComboBox<CompileOrder> myCompileOrderCmb;
  private JPanel myCompilerOptionsPanel;
  private JComboBox<NameHashing> myNameHashingCmb;
  private JPanel myNameHashingPnl;
  private ScalaApplicationSettings mySettings;

  public ScalaApplicationSettingsForm(ScalaApplicationSettings settings) {
    mySettings = settings;
    
    myEnableCompileServer.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateCompilationServerSettingsPanel();
      }
    });

    initCompilerTypeCmb();
    initNameHashingPanel();
    initCompileOrderCmb();

    ProjectSdksModel model = new ProjectSdksModel();
    model.reset(null);

    myCompilationServerSdk = new JdkComboBox(model);
    myCompilationServerSdk.insertItemAt(new JdkComboBox.NoneJdkComboBoxItem(), 0);

    mySdkPanel.add(myCompilationServerSdk, BorderLayout.CENTER);
    mySdkPanel.setSize(mySdkPanel.getPreferredSize());

    myNote.setForeground(JBColor.GRAY);

    delaySpinner.setEnabled(showTypeInfoOnCheckBox.isSelected());
    showTypeInfoOnCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        delaySpinner.setEnabled(showTypeInfoOnCheckBox.isSelected());
      }
    });
    delaySpinner.setValue(mySettings.SHOW_TYPE_TOOLTIP_DELAY);

    updateCompilationServerSettingsPanel();
  }

  private void initCompilerTypeCmb() {
    final List<IncrementalType> values = Arrays.asList(IncrementalType.values());
    myIncrementalTypeCmb.setModel(new ListComboBoxModel<IncrementalType>(values));
    myIncrementalTypeCmb.setSelectedItem(mySettings.INCREMENTAL_TYPE);
    myIncrementalTypeCmb.setRenderer(new ListCellRendererWrapper<IncrementalType>() {
      @Override
      public void customize(JList list, IncrementalType value, int index, boolean selected, boolean hasFocus) {
        if (value == IncrementalType.SBT) setText("SBT incremental compiler");
        if (value == IncrementalType.IDEA) setText("IntelliJ IDEA");
      }
    });
    myIncrementalTypeCmb.setToolTipText("Rebuild is required after change");
  }

  private void initNameHashingPanel() {
    final List<NameHashing> values = Arrays.asList(NameHashing.values());
    myNameHashingCmb.setModel(new ListComboBoxModel<NameHashing>(values));
    myNameHashingCmb.setSelectedItem(mySettings.NAME_HASHING);
    myNameHashingCmb.setRenderer(new ListCellRendererWrapper<NameHashing>() {
      @Override
      public void customize(JList list, NameHashing value, int index, boolean selected, boolean hasFocus) {
        if (value == NameHashing.DEFAULT) setText("Default");
        if (value == NameHashing.ENABLED) setText("Enabled");
        if (value == NameHashing.DISABLED) setText("Disabled");
      }
    });
    myNameHashingPnl.setToolTipText("Experimental option for faster incremental compilation");
    myNameHashingPnl.setVisible(ScalaApplicationSettings.getInstance().INCREMENTAL_TYPE == IncrementalType.SBT);
    myIncrementalTypeCmb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myNameHashingPnl.setVisible(myIncrementalTypeCmb.getSelectedItem() == IncrementalType.SBT);
      }
    });
  }

  private void initCompileOrderCmb() {
    final List<CompileOrder> values = Arrays.asList(CompileOrder.values());
    myCompileOrderCmb.setModel(new ListComboBoxModel<CompileOrder>(values));
    myCompileOrderCmb.setSelectedItem(mySettings.COMPILE_ORDER);
    myCompileOrderCmb.setRenderer(new ListCellRendererWrapper<CompileOrder>() {
      @Override
      public void customize(JList list, CompileOrder value, int index, boolean selected, boolean hasFocus) {
        if (value == CompileOrder.Mixed) setText("Mixed");
        if (value == CompileOrder.JavaThenScala) setText("Java then Scala");
        if (value == CompileOrder.ScalaThenJava) setText("Scala then Java");
      }
    });
  }

  private void updateCompilationServerSettingsPanel() {
    setDescendantsEnabledIn(myCompilationServerPanel, myEnableCompileServer.isSelected());
    myNote.setEnabled(true);
  }

  private static void setDescendantsEnabledIn(JComponent root, boolean b) {
    for (Component child : root.getComponents()) {
      child.setEnabled(b);
      if (child instanceof JComponent) {
        setDescendantsEnabledIn((JComponent) child, b);
      }
    }
  }

  @Nls
  public String getDisplayName() {
    return "Scala";
  }

  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  public JComponent createComponent() {
    return myContentPanel;
  }

  public boolean isModified() {
    Sdk sdk = myCompilationServerSdk.getSelectedJdk();
    String sdkName = sdk == null ? null : sdk.getName();

    if (showTypeInfoOnCheckBox.isSelected() != mySettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER) return true;
    if (!delaySpinner.getValue().equals(mySettings.SHOW_TYPE_TOOLTIP_DELAY)) return true;

    return !(myEnableCompileServer.isSelected() == mySettings.COMPILE_SERVER_ENABLED &&
        myCompilationServerPort.getText().equals(mySettings.COMPILE_SERVER_PORT) &&
        ComparatorUtil.equalsNullable(sdkName, mySettings.COMPILE_SERVER_SDK) &&
        myCompilationServerMaximumHeapSize.getText().equals(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE) &&
        myCompilationServerJvmParameters.getText().equals(mySettings.COMPILE_SERVER_JVM_PARAMETERS) &&
        myIncrementalTypeCmb.getModel().getSelectedItem().equals(mySettings.INCREMENTAL_TYPE) &&
        myCompileOrderCmb.getModel().getSelectedItem().equals(mySettings.COMPILE_ORDER) &&
        myNameHashingCmb.getModel().getSelectedItem().equals(mySettings.NAME_HASHING));
  }

  public void apply() throws ConfigurationException {
    mySettings.INCREMENTAL_TYPE = (IncrementalType) myIncrementalTypeCmb.getModel().getSelectedItem();
    mySettings.NAME_HASHING = (NameHashing) myNameHashingCmb.getModel().getSelectedItem();
    mySettings.COMPILE_ORDER = (CompileOrder) myCompileOrderCmb.getModel().getSelectedItem();
    mySettings.COMPILE_SERVER_ENABLED = myEnableCompileServer.isSelected();
    mySettings.COMPILE_SERVER_PORT = myCompilationServerPort.getText();

    Sdk sdk = myCompilationServerSdk.getSelectedJdk();
    mySettings.COMPILE_SERVER_SDK = sdk == null ? null : sdk.getName();

    mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE = myCompilationServerMaximumHeapSize.getText();
    mySettings.COMPILE_SERVER_JVM_PARAMETERS = myCompilationServerJvmParameters.getText();
    mySettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER = showTypeInfoOnCheckBox.isSelected();
    mySettings.SHOW_TYPE_TOOLTIP_DELAY = (Integer) delaySpinner.getValue();

    // TODO
//    boolean externalCompiler = CompilerWorkspaceConfiguration.getInstance(myProject).USE_COMPILE_SERVER;
//
//    if (!externalCompiler || !myEnableCompileServer.isSelected()) {
//      myProject.getComponent(CompileServerLauncher.class).stop();
//    }
//    myProject.getComponent(CompileServerManager.class).configureWidget();
  }

  public void reset() {
    myEnableCompileServer.setSelected(mySettings.COMPILE_SERVER_ENABLED);
    myCompilationServerPort.setText(mySettings.COMPILE_SERVER_PORT);

    Sdk sdk = mySettings.COMPILE_SERVER_SDK == null
        ? null
        : ProjectJdkTable.getInstance().findJdk(mySettings.COMPILE_SERVER_SDK);
    myCompilationServerSdk.setSelectedJdk(sdk);

    myCompilationServerMaximumHeapSize.setText(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE);
    myCompilationServerJvmParameters.setText(mySettings.COMPILE_SERVER_JVM_PARAMETERS);
    showTypeInfoOnCheckBox.setSelected(mySettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER);
    delaySpinner.setValue(mySettings.SHOW_TYPE_TOOLTIP_DELAY);
  }

  public void disposeUIResources() {
  }
}
