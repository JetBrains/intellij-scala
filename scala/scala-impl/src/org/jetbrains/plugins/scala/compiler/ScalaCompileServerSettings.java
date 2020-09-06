package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.UUID;

/**
 * @author Pavel Fatin
 */
@State(
        name = "ScalaSettings",
        storages = {@Storage("scala.xml")},
        reportStatistic = true
)
public class ScalaCompileServerSettings implements PersistentStateComponent<ScalaCompileServerSettings> {
  public boolean SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER = false;
  @ReportValue
  public int SHOW_TYPE_TOOLTIP_DELAY = 500;

  public boolean COMPILE_SERVER_ENABLED = true;

  //is not accessible from UI, but is serialized and used in jps-plugin
  public int COMPILE_SERVER_PORT = 3200;
  public String COMPILE_SERVER_ID = UUID.randomUUID().toString();

  public boolean USE_DEFAULT_SDK = true;
  public String COMPILE_SERVER_SDK;

  public String COMPILE_SERVER_MAXIMUM_HEAP_SIZE = "1024";
  public String COMPILE_SERVER_JVM_PARAMETERS = "-server -Xss1m";

  //in minutes
  @ReportValue
  public int COMPILE_SERVER_SHUTDOWN_DELAY = 120;
  public boolean COMPILE_SERVER_SHUTDOWN_IDLE = true;

  public boolean USE_PROJECT_HOME_AS_WORKING_DIR = false;

  public ScalaCompileServerSettings getState() {
    return this;
  }

  public void loadState(ScalaCompileServerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static ScalaCompileServerSettings getInstance() {
    return ServiceManager.getService(ScalaCompileServerSettings.class);
  }
}
