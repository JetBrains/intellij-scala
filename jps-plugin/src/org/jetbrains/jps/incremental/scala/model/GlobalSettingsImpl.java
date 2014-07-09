package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.plugin.scala.compiler.CompileOrder;
import org.jetbrains.plugin.scala.compiler.IncrementalType;
import org.jetbrains.plugin.scala.compiler.NameHashing;

/**
 * @author Pavel Fatin
 */
public class GlobalSettingsImpl extends JpsElementBase<GlobalSettingsImpl> implements GlobalSettings {
  public static final GlobalSettings DEFAULT = new GlobalSettingsImpl(new State());

  private State myState;

  public GlobalSettingsImpl(State state) {
    myState = state;
  }

  public boolean isCompileServerEnabled() {
    return myState.COMPILE_SERVER_ENABLED;
  }

  public int getCompileServerPort() {
    return myState.COMPILE_SERVER_PORT;
  }

  public String getCompileServerSdk() {
    return myState.COMPILE_SERVER_SDK;
  }

  public IncrementalType getIncrementalType() {
    return myState.INCREMENTAL_TYPE;
  }

  public NameHashing getNameHashing() {
    return myState.NAME_HASHING;
  }

  public CompileOrder getCompileOrder() {
    return myState.COMPILE_ORDER;
  }

  @NotNull
  @Override
  public GlobalSettingsImpl createCopy() {
    return new GlobalSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull GlobalSettingsImpl compilerSettings) {
    // do nothing
  }

  public static class State {
    public boolean COMPILE_SERVER_ENABLED = true;

    public int COMPILE_SERVER_PORT = 3200;

    public String COMPILE_SERVER_SDK;

    public IncrementalType INCREMENTAL_TYPE = IncrementalType.IDEA;

    public NameHashing NAME_HASHING = NameHashing.DEFAULT;

    public CompileOrder COMPILE_ORDER = CompileOrder.Mixed;
  }
}
