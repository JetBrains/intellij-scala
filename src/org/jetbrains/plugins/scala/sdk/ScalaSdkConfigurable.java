package org.jetbrains.plugins.scala.sdk;

import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.GuiUtils;

import javax.swing.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.09.2006
 * Time: 19:45:42
 */
public class ScalaSdkConfigurable implements AdditionalDataConfigurable {
    private Sdk scalaSdk;

    public void setSdk(Sdk sdk) {
        scalaSdk = sdk;
    }

    public JComponent createComponent() {
        return new JLabel("bar");
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