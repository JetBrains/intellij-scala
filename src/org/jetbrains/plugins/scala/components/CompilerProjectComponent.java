package org.jetbrains.plugins.scala.components;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.compiler.ScalacSettings;
import org.jetbrains.plugins.scala.compiler.ScalaCompiler;
import org.jetbrains.plugins.scala.ScalaFileType;

import java.util.HashSet;
import java.util.Arrays;

/**
 * User: Alexander Podkhalyuzin, Pavel Fatin
 * Date: 05.10.2008
 */
public class CompilerProjectComponent implements ProjectComponent  {
  private Project myProject;

  public CompilerProjectComponent(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    ScalacSettings settings = ScalacSettings.getInstance(myProject);
    if (settings.SCALAC_BEFORE) {
      configureToCompileScalaFirst();
    } else {
      configureToCompileJavaFirst();
    }
  }

  public void configureToCompileScalaFirst() {
    removeScalaCompilers();
    HashSet<FileType> inputSet = new HashSet<FileType>(Arrays.asList(ScalaFileType.SCALA_FILE_TYPE, StdFileTypes.JAVA));
    HashSet<FileType> outputSet = new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA, StdFileTypes.CLASS));
    getCompilerManager().addTranslatingCompiler(new ScalaCompiler(myProject, false), inputSet, outputSet);
    getCompilerManager().addTranslatingCompiler(new ScalaCompiler(myProject, true), inputSet, outputSet);
  }

  public void configureToCompileJavaFirst() {
    removeScalaCompilers();
    getCompilerManager().addCompiler(new ScalaCompiler(myProject, false));
    getCompilerManager().addCompiler(new ScalaCompiler(myProject, true));
  }

  private void removeScalaCompilers() {
    for (ScalaCompiler compiler: getCompilerManager().getCompilers(ScalaCompiler.class)) {
      getCompilerManager().removeCompiler(compiler);
    }
  }

  private CompilerManager getCompilerManager() {
    return CompilerManager.getInstance(myProject);
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "Component to change compilers order";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
