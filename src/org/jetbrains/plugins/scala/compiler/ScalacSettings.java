package org.jetbrains.plugins.scala.compiler;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.project.Project;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.09.2008
 */

@State(
  name = "ScalacSettings",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class ScalacSettings implements PersistentStateComponent<Element>, ProjectComponent {
  public String ADDITIONAL_OPTIONS_STRING = "";
  public int MAXIMUM_HEAP_SIZE = 128;

  public Element getState() {
     try {
      final Element e = new Element("state");
      DefaultJDOMExternalizer.writeExternal(this, e);
      return e;
    }
    catch (WriteExternalException e1) {
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException ignore) {
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ScalacSettings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static ScalacSettings getInstance(Project project) {
    return project.getComponent(ScalacSettings.class);
  }
}
