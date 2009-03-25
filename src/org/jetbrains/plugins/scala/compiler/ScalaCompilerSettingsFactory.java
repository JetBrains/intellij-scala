package org.jetbrains.plugins.scala.compiler;

import com.intellij.compiler.CompilerSettingsFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.10.2008
 */
public class ScalaCompilerSettingsFactory implements CompilerSettingsFactory{
  public Configurable create(Project project) {
    return new ScalacConfigurable(ScalacSettings.getInstance(project), project);
  }
}
