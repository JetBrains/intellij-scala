package org.jetbrains.plugins.scala.module;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.actions.ScalaSdkChooser;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 16:31:20
 */
public class ScalaLoader implements ApplicationComponent, Configurable {
    private ScalaSdkChooser sdkChooserDialog;

    public ScalaLoader() {
    }

    public void initComponent() {
        ApplicationManager.getApplication().runWriteAction(
                new Runnable() {
                    public void run() {
                        FileTypeManager.getInstance().registerFileType(ScalaFileType.SCALA_FILE_TYPE, new String[]{"scala"});
                    }
                }
        );
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return "ScalaLoader";
    }

    @Nls
    public String getDisplayName() {
        return "Scala options";
    }

    public Icon getIcon() {
        return null;
    }

    @Nullable
    @NonNls
    public String getHelpTopic() {
        return null;
    }

    public JComponent createComponent() {
        sdkChooserDialog = new ScalaSdkChooser();
        sdkChooserDialog.addSdkDialog();
        return sdkChooserDialog;
    }

    public boolean isModified() {
        return false;
    }

    public void apply() throws ConfigurationException {
        sdkChooserDialog.addSdkDialog();
        System.out.println(sdkChooserDialog.getSdkGlobalPath());
    }

    public void reset() {
    }

    public void disposeUIResources() {
    }
}
