package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author Pavel Fatin
 */
public class ProjectSettingsImpl extends JpsElementBase<ProjectSettingsImpl> implements ProjectSettings {
  public static final ProjectSettings DEFAULT = new ProjectSettingsImpl(new State());

  private State myState;

  public ProjectSettingsImpl(State state) {
    myState = state;
  }

  public Order getCompilationOrder() {
    return myState.SCALAC_BEFORE ? Order.ScalaThenJava : Order.JavaThenScala;
  }

  @NotNull
  @Override
  public ProjectSettingsImpl createCopy() {
    return new ProjectSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull ProjectSettingsImpl compilerSettings) {
    // do nothing
  }

  public static class State {
    public boolean SCALAC_BEFORE = true;
  }
}
