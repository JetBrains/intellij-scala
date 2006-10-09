package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.lang.ParserDefinition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Author: Ilya Sergey
 * Date: 09.10.2006
 * Time: 21:23:48
 */
public abstract class ScalaParserDefinitionFactory implements ApplicationComponent {

  public static ScalaParserDefinitionFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(ScalaParserDefinitionFactory.class);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "scala.ScalaParserDefinitionFactory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public abstract ParserDefinition createScalaParserDefinition();
}
