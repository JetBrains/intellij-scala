package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Pavel Fatin
 */
@State(name = "ScalaSettings", storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/scala.xml")})
public class ScalaApplicationSettings implements PersistentStateComponent<ScalaApplicationSettings> {
  public boolean SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER = false;
  public int SHOW_TYPE_TOOLTIP_DELAY = 500;

  public boolean SHOW_EXTERNAL_COMPILER_INTRO = true;

  public boolean COMPILE_SERVER_ENABLED = true;

  private String[] incrTypes = {"SBT", "IDEA"};
  public final List<String> INCREMENTAL_TYPES = Collections.unmodifiableList(Arrays.asList(incrTypes));

  private String[] cOrders = {"Mixed", "JavaThenScala", "ScalaThenJava"};
  public final List<String> COMPILE_ORDERS = Collections.unmodifiableList(Arrays.asList(cOrders));

  public String INCREMENTAL_TYPE = INCREMENTAL_TYPES.get(0);
  public String COMPILE_ORDER = COMPILE_ORDERS.get(0);
  public String COMPILE_SERVER_PORT = "3200";
  public String COMPILE_SERVER_SDK;
  public String COMPILE_SERVER_MAXIMUM_HEAP_SIZE = "1024";
  public String COMPILE_SERVER_JVM_PARAMETERS = "-server -Xss1m -XX:MaxPermSize=256m";

  public ScalaApplicationSettings getState() {
    return this;
  }

  public void loadState(ScalaApplicationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static ScalaApplicationSettings getInstance() {
    return ServiceManager.getService(ScalaApplicationSettings.class);
  }
}
