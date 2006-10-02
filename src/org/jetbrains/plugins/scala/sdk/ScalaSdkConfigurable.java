package org.jetbrains.plugins.scala.sdk;

import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.TextFieldWithStoredHistory;

import javax.swing.*;
import java.awt.*;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaBundle;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.09.2006
 * Time: 19:45:42
 */
public class ScalaSdkConfigurable implements AdditionalDataConfigurable {
    private Sdk scalaSdk;
    @NonNls private static final String SANDBOX_HISTORY = "SCALA_SANDBOX_HISTORY";

    private JLabel mySandboxHomeLabel = new JLabel(ScalaBundle.message("sandbox.home.label"));
    private TextFieldWithStoredHistory mySandboxHome = new TextFieldWithStoredHistory(SANDBOX_HISTORY);

    public void setSdk(Sdk sdk) {
        scalaSdk = sdk;
    }

    public JComponent createComponent() {
        JPanel panel = new JPanel(new GridLayout());

        return panel;
    }

    public boolean isModified() {
        return false;
    }

    public void apply() throws ConfigurationException {
    }

    public void reset() {
    }

    public void disposeUIResources() {
    }
}