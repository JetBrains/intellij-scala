package org.jetbrains.plugins.scala.compiler;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import scala.Some$;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

@SuppressWarnings("deprecation")
public class ScalaCompileServerForm implements Configurable {
    private JPanel myJvmSettingsPanel;
    private RawCommandLineEditor myCompilationServerJvmParameters;
    private JTextField myCompilationServerMaximumHeapSize;
    private JCheckBox myEnableCompileServer;
    private JPanel myContentPanel;
    private final JdkComboBox myCompilationServerSdk;
    private JPanel mySdkPanel;
    private JCheckBox myProjectHomeChb;
    private JCheckBox myShutdownServerCheckBox;
    private JSpinner myShutdownDelay;
    private JSpinner myParallelism;
    private JCheckBox myParallelCompilation;
    private JPanel myParallelCompilationSettingsPanel;
    private JPanel myShutdownSettingsPanel;
    private JPanel myAdvancedSettingsPanel;
    private JPanel myUseProjectHomePanel;
    private TitledSeparator myJvmTitle;
    private final ScalaCompileServerSettings mySettings;
    private final ProjectSdksModelWithDefault sdkModel;

    public static final class SearchFilter {
        // TODO: will not work with localized IDEA
        public static String USE_COMPILE_SERVER_FOR_SCALA = "use scala compile server";
    }

    public ScalaCompileServerForm(Project project) {
        mySettings = ScalaCompileServerSettings.getInstance();

        myProjectHomeChb = new JCheckBox(ScalaBundle.message("compile.server.use.project.home"));
        myUseProjectHomePanel.add(UI.PanelFactory.panel(myProjectHomeChb).withTooltip(ScalaBundle.message("compile.server.new.project.restart")).createPanel());

        myEnableCompileServer.addChangeListener(e -> updateJvmSettingsPanel());
        myParallelCompilation.addChangeListener(e -> updateParallelCompilationSettingsPanel());
        myShutdownServerCheckBox.addChangeListener(e -> updateShutdownSettingsPanel());

        sdkModel = new ProjectSdksModelWithDefault();
        inEventDispatchThread(() -> sdkModel.reset(project));

        myCompilationServerSdk = new JdkComboBox(null, sdkModel, null, null, null, null);
        myCompilationServerSdk.showNoneSdkItem();

        mySdkPanel.add(UI.PanelFactory.panel(myCompilationServerSdk).withTooltip(ScalaBundle.message("compile.server.description")).createPanel(), BorderLayout.CENTER);

        myShutdownDelay.setModel(new SpinnerNumberModel(mySettings.COMPILE_SERVER_SHUTDOWN_DELAY, 0, 24 * 60, 1));
        myParallelism.setModel(new SpinnerNumberModel(mySettings.COMPILE_SERVER_PARALLELISM, 1, 9999, 1));

        updateJvmSettingsPanel();
    }

    private void updateJvmSettingsPanel() {
        boolean compileServerEnabled = myEnableCompileServer.isSelected();
        setDescendantsEnabledIn(myJvmSettingsPanel, compileServerEnabled);
        setDescendantsEnabledIn(myAdvancedSettingsPanel, compileServerEnabled);
        myJvmTitle.setEnabled(compileServerEnabled);
    }

    private void updateParallelCompilationSettingsPanel() {
        setDescendantsEnabledIn(myParallelCompilationSettingsPanel, myParallelCompilation.isSelected());
    }

    private void updateShutdownSettingsPanel() {
        setDescendantsEnabledIn(myShutdownSettingsPanel, myShutdownServerCheckBox.isSelected());
    }

    private static void inEventDispatchThread(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) runnable.run();
        else ApplicationManager.getApplication().invokeAndWait(runnable);
    }

    private static void setDescendantsEnabledIn(JComponent root, boolean b) {
        for (Component child : root.getComponents()) {
            child.setEnabled(b);
            if (child instanceof JComponent) {
                setDescendantsEnabledIn((JComponent) child, b);
            }
        }
    }

    @Override
    @Nls
    public String getDisplayName() {
        return ScalaBundle.message("scala.compile.server.title");
    }

    @Override
    @Nullable
    public String getHelpTopic() {
        return null;
    }

    @Override
    @Nullable
    public JComponent createComponent() {
        return myContentPanel;
    }

    @Override
    public boolean isModified() {
        Sdk sdk = myCompilationServerSdk.getSelectedJdk();
        String sdkName = sdk == null ? null : sdk.getName();

        return !(myEnableCompileServer.isSelected() == mySettings.COMPILE_SERVER_ENABLED &&
                sdkModel.isDefault(sdk) == mySettings.USE_DEFAULT_SDK &&
                ComparatorUtil.equalsNullable(sdkName, mySettings.COMPILE_SERVER_SDK) &&
                myCompilationServerMaximumHeapSize.getText().equals(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE) &&
                myCompilationServerJvmParameters.getText().equals(mySettings.COMPILE_SERVER_JVM_PARAMETERS) &&
                myProjectHomeChb.isSelected() == mySettings.USE_PROJECT_HOME_AS_WORKING_DIR &&
                myShutdownServerCheckBox.isSelected() == mySettings.COMPILE_SERVER_SHUTDOWN_IDLE &&
                (Integer) (myShutdownDelay.getModel().getValue()) == mySettings.COMPILE_SERVER_SHUTDOWN_DELAY &&
                (Integer) (myParallelism.getModel().getValue()) == mySettings.COMPILE_SERVER_PARALLELISM &&
                myParallelCompilation.isSelected() == mySettings.COMPILE_SERVER_PARALLEL_COMPILATION
        );
    }

    @Override
    public void apply() {
        mySettings.COMPILE_SERVER_ENABLED = myEnableCompileServer.isSelected();
        if (!mySettings.COMPILE_SERVER_ENABLED) {
            CompileServerLauncher.stop(0, Some$.MODULE$.apply("compile server disabled from settings"));
        }

        Sdk sdk = myCompilationServerSdk.getSelectedJdk();
        mySettings.USE_DEFAULT_SDK = sdkModel.isDefault(sdk);
        mySettings.COMPILE_SERVER_SDK = sdk == null ? null : sdk.getName();

        mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE = myCompilationServerMaximumHeapSize.getText();
        mySettings.COMPILE_SERVER_JVM_PARAMETERS = myCompilationServerJvmParameters.getText();

        mySettings.USE_PROJECT_HOME_AS_WORKING_DIR = myProjectHomeChb.isSelected();

        mySettings.COMPILE_SERVER_SHUTDOWN_IDLE = myShutdownServerCheckBox.isSelected();
        mySettings.COMPILE_SERVER_SHUTDOWN_DELAY = (Integer) (myShutdownDelay.getModel().getValue());

        Integer newParallelism = (Integer) (myParallelism.getModel().getValue());
        Boolean newParallelCompilation = myParallelCompilation.isSelected();
        if (mySettings.COMPILE_SERVER_PARALLELISM != newParallelism ||
                mySettings.COMPILE_SERVER_PARALLEL_COMPILATION != newParallelCompilation) {
            BuildManager.getInstance().clearState(); // We have to cancel all preloaded builds. SCL-18512
        }
        mySettings.COMPILE_SERVER_PARALLELISM = newParallelism;
        mySettings.COMPILE_SERVER_PARALLEL_COMPILATION = newParallelCompilation;
    }

    @Override
    public void reset() {
        myEnableCompileServer.setSelected(mySettings.COMPILE_SERVER_ENABLED);

        Sdk sdk = sdkModel.sdkFrom(mySettings);
        myCompilationServerSdk.setSelectedJdk(sdk);

        myCompilationServerMaximumHeapSize.setText(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE);
        myCompilationServerJvmParameters.setText(mySettings.COMPILE_SERVER_JVM_PARAMETERS);

        myShutdownServerCheckBox.setSelected(mySettings.COMPILE_SERVER_SHUTDOWN_IDLE);
        myShutdownDelay.getModel().setValue(mySettings.COMPILE_SERVER_SHUTDOWN_DELAY);
        myParallelism.getModel().setValue(mySettings.COMPILE_SERVER_PARALLELISM);
        myParallelCompilation.setSelected(mySettings.COMPILE_SERVER_PARALLEL_COMPILATION);

        myProjectHomeChb.setSelected(mySettings.USE_PROJECT_HOME_AS_WORKING_DIR);
    }

    @Override
    public void disposeUIResources() {
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
        myContentPanel = new JPanel();
        myContentPanel.setLayout(new GridLayoutManager(11, 4, new Insets(0, 0, 0, 0), -1, -1));
        myAdvancedSettingsPanel = new JPanel();
        myAdvancedSettingsPanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(myAdvancedSettingsPanel, new GridConstraints(1, 0, 7, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        myAdvancedSettingsPanel.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myShutdownServerCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(myShutdownServerCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "compile.server.shutdown.if.idle.for"));
        panel1.add(myShutdownServerCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShutdownSettingsPanel = new JPanel();
        myShutdownSettingsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(myShutdownSettingsPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myShutdownDelay = new JSpinner();
        myShutdownSettingsPanel.add(myShutdownDelay, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "minutes"));
        myShutdownSettingsPanel.add(label1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myShutdownSettingsPanel.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        myUseProjectHomePanel = new JPanel();
        myUseProjectHomePanel.setLayout(new BorderLayout(0, 0));
        myAdvancedSettingsPanel.add(myUseProjectHomePanel, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        myAdvancedSettingsPanel.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myParallelCompilation = new JCheckBox();
        this.$$$loadButtonText$$$(myParallelCompilation, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "compile.server.parallel.compilation"));
        panel2.add(myParallelCompilation, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myParallelCompilationSettingsPanel = new JPanel();
        myParallelCompilationSettingsPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(myParallelCompilationSettingsPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myParallelism = new JSpinner();
        myParallelCompilationSettingsPanel.add(myParallelism, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "compile.server.parallelism"));
        myParallelCompilationSettingsPanel.add(label2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel2.add(spacer2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        myContentPanel.add(spacer3, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myJvmSettingsPanel = new JPanel();
        myJvmSettingsPanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(myJvmSettingsPanel, new GridConstraints(9, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label3 = new JLabel();
        label3.setEnabled(true);
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "jvm.options"));
        myJvmSettingsPanel.add(label3, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCompilationServerJvmParameters = new RawCommandLineEditor();
        myCompilationServerJvmParameters.setDialogCaption(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "compile.server.jvm.command.line.parameters"));
        myCompilationServerJvmParameters.setEnabled(true);
        myJvmSettingsPanel.add(myCompilationServerJvmParameters, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(250, -1), new Dimension(544, 27), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setEnabled(true);
        this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "jvm.maximum.heap.size.mb"));
        myJvmSettingsPanel.add(label4, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCompilationServerMaximumHeapSize = new JTextField();
        myCompilationServerMaximumHeapSize.setColumns(5);
        myCompilationServerMaximumHeapSize.setEnabled(true);
        myJvmSettingsPanel.add(myCompilationServerMaximumHeapSize, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        this.$$$loadLabelText$$$(label5, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "jdk"));
        myJvmSettingsPanel.add(label5, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        mySdkPanel = new JPanel();
        mySdkPanel.setLayout(new BorderLayout(0, 0));
        mySdkPanel.setEnabled(false);
        myJvmSettingsPanel.add(mySdkPanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myEnableCompileServer = new JCheckBox();
        this.$$$loadButtonText$$$(myEnableCompileServer, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "compile.server.use.for.scala"));
        myContentPanel.add(myEnableCompileServer, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myJvmTitle = new TitledSeparator();
        myJvmTitle.setText("JVM");
        myContentPanel.add(myJvmTitle, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    private String $$$getMessageFromBundle$$$(String path, String key) {
        ResourceBundle bundle;
        try {
            Class<?> thisClass = this.getClass();
            if ($$$cachedGetBundleMethod$$$ == null) {
                Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
                $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
            }
            bundle = (ResourceBundle) $$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
        } catch (Exception e) {
            bundle = ResourceBundle.getBundle(path);
        }
        return bundle.getString(key);
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myContentPanel;
    }

}
