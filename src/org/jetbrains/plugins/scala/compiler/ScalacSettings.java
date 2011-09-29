package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.plugins.scala.config.LibraryLevel;

/**
 * User: Alexander Podkhalyuzin, Pavel Fatin
 * Date: 22.09.2008
 */

@State(
    name = "ScalacSettings",
    storages = {
        @Storage(id = "default", file = "$PROJECT_FILE$")
        , @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/scala_compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class ScalacSettings implements PersistentStateComponent<ScalacSettings> {
  public boolean SCALAC_BEFORE = true;
  public String COMPILER_LIBRARY_NAME = "";
  public LibraryLevel COMPILER_LIBRARY_LEVEL = null;
  public boolean INTERNAL_SERVER = true;
  public String MAXIMUM_HEAP_SIZE = "1024";
  public String FSC_OPTIONS = "";
  public String VM_PARAMETERS = "-Xms768m -Xss1m -server";
  public String REMOTE_HOST = "";
  public String REMOTE_PORT = "";
  public String SHARED_DIRECTORY = "";

  public ScalacSettings getState() {
    return this;
  }

  public void loadState(ScalacSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static ScalacSettings getInstance(Project project) {
    return ServiceManager.getService(project, ScalacSettings.class);
  }
}
