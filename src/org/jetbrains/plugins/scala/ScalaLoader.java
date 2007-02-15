package org.jetbrains.plugins.scala;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.actions.ScalaSdkChooser;
import org.jetbrains.plugins.scala.compiler.ScalaCompiler;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;

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
    loadScala();
  }

  public static void loadScala() {
    ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                FileTypeManager.getInstance().registerFileType(ScalaFileType.SCALA_FILE_TYPE, "scala");
              }
            }
    );


/*
    System.out.println("STF="+ScalaToolsFactory.getInstance());
    assert (ScalaToolsFactory.getInstance() != null);
*/

    CompletionUtil.registerCompletionData(ScalaFileType.SCALA_FILE_TYPE,
            ScalaToolsFactory.getInstance().createScalaCompletionData());

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(Project project) {
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.addCompiler(new ScalaCompiler(project));
        compilerManager.addCompilableFileType(ScalaFileType.SCALA_FILE_TYPE);
      }
    });
    

  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "Scala Loader";
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

    return sdkChooserDialog;
  }

  public boolean isModified() {
    System.out.println("modify: " + sdkChooserDialog.getSdkGlobalPath());
    return sdkChooserDialog.getSdkGlobalPath() != null && !sdkChooserDialog.getSdkGlobalPath().equals("");
  }

  public void apply() throws ConfigurationException {
    System.out.println("apply: " + sdkChooserDialog.getSdkGlobalPath());
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }
}