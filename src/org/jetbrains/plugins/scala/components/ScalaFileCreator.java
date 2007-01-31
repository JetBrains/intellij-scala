package org.jetbrains.plugins.scala.components;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;

/**
 * @author Ilya.Sergey
 */
public class ScalaFileCreator implements ApplicationComponent {


  public static final String SCALA_FILE_TEMPLATE = ScalaBundle.message("new.scala.template");

  @NonNls
  @NotNull
  public String getComponentName() {
    return ScalaComponents.SCALA_FILE_CREATOR;
  }


  private void registerScalaScriptTemplate() {
    String templateText = ScalaBundle.message("template.file.text");
    FileTemplate scalaTemplate = FileTemplateManager.getInstance().addTemplate(SCALA_FILE_TEMPLATE,
            ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension());
    ((FileTemplateImpl) scalaTemplate).setInternal(true);
    scalaTemplate.setText(templateText);
  }


  public void initComponent() {
    removeComponent();
    FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance();
    FileTemplate scalaTemplate = fileTemplateManager.getTemplate(SCALA_FILE_TEMPLATE);
    if (scalaTemplate == null) {
      registerScalaScriptTemplate();
    }
  }

  public void disposeComponent() {
    removeComponent();
  }


  private void removeComponent() {
    FileTemplate scalaTemplate = FileTemplateManager.getInstance().getTemplate(SCALA_FILE_TEMPLATE);
    if (scalaTemplate != null)
      FileTemplateManager.getInstance().removeTemplate(scalaTemplate, false);
    scalaTemplate = FileTemplateManager.getInstance().getTemplate("Scala file");
    if (scalaTemplate != null)
      FileTemplateManager.getInstance().removeTemplate(scalaTemplate, false);
  }


}
