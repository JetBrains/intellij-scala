package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.UUID;

@State(
        name = "ScalaSettings",
        storages = {@Storage("scala.xml")},
        reportStatistic = true,
        category = SettingsCategory.TOOLS
)
public class ScalaCompileServerSettings implements PersistentStateComponent<ScalaCompileServerSettings> {
  //ATTENTION: these field names should be the same as in
  //org.jetbrains.jps.incremental.scala.model.impl.GlobalSettingsImpl.State (see it's JavaDoc)
  public boolean COMPILE_SERVER_ENABLED = true;
  public int COMPILE_SERVER_PORT = 3200;
  public String COMPILE_SERVER_SDK;

  //is not accessible from UI, but is serialized and used in jps-plugin
  public String COMPILE_SERVER_ID = UUID.randomUUID().toString();

  public boolean USE_DEFAULT_SDK = true;

  public String COMPILE_SERVER_MAXIMUM_HEAP_SIZE = "2048";
  public String COMPILE_SERVER_JVM_PARAMETERS = "-server -Xss2m -XX:+UseParallelGC -XX:MaxInlineLevel=20";
  public int COMPILE_SERVER_PARALLELISM = 4;
  public boolean COMPILE_SERVER_PARALLEL_COMPILATION = true;

  //in minutes
  @ReportValue
  public int COMPILE_SERVER_SHUTDOWN_DELAY = 120;
  public boolean COMPILE_SERVER_SHUTDOWN_IDLE = true;

  public boolean USE_PROJECT_HOME_AS_WORKING_DIR = false;

  @TestOnly
  transient public String CUSTOM_WORKING_DIR_FOR_TESTS = null;

  @Override
  public ScalaCompileServerSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull ScalaCompileServerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static ScalaCompileServerSettings getInstance() {
    return ApplicationManager.getApplication().getService(ScalaCompileServerSettings.class);
  }
}
