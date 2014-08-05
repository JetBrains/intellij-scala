package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.plugin.scala.compiler.CompileOrder;
import org.jetbrains.plugin.scala.compiler.IncrementalType;
import org.jetbrains.plugin.scala.compiler.NameHashing;

/**
 * @author Pavel Fatin
 */
@State(name = "ScalaSettings", storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/scala.xml")})
public class ScalaApplicationSettings implements PersistentStateComponent<ScalaApplicationSettings> {
  public boolean SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER = false;
  public int SHOW_TYPE_TOOLTIP_DELAY = 500;

  public boolean COMPILE_SERVER_ENABLED = true;

  public IncrementalType INCREMENTAL_TYPE = IncrementalType.IDEA;
  public NameHashing NAME_HASHING = NameHashing.DEFAULT;
  public CompileOrder COMPILE_ORDER = CompileOrder.Mixed;

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
