package org.jetbrains.jps.incremental.scala.model.impl;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.GlobalSettings;
import org.jetbrains.jps.model.ex.JpsElementBase;

public class GlobalSettingsImpl extends JpsElementBase<GlobalSettingsImpl> implements GlobalSettings {
  public static final GlobalSettings DEFAULT = new GlobalSettingsImpl(new State());

  private final State myState;

  public GlobalSettingsImpl(State state) {
    myState = state;
  }

  @Override
  public boolean isCompileServerEnabled() {
    return myState.COMPILE_SERVER_ENABLED;
  }

  @Override
  public int getCompileServerPort() {
    return myState.COMPILE_SERVER_PORT;
  }

  @Override
  public String getCompileServerSdk() {
    return myState.COMPILE_SERVER_SDK;
  }

  /**
   * ATTENTION: these names should be the same in org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
   *
   * @see JpsScalaModelSerializerExtension.GlobalSettingsSerializer
   */
  @SuppressWarnings("JavadocReference")
  public static class State {
    public boolean COMPILE_SERVER_ENABLED = true;
    public int COMPILE_SERVER_PORT = 3200;
    public String COMPILE_SERVER_SDK;
  }
}
