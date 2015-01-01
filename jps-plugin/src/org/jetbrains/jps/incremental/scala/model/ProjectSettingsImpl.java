package org.jetbrains.jps.incremental.scala.model;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class ProjectSettingsImpl extends JpsElementBase<ProjectSettingsImpl> implements ProjectSettings {
  public static final ProjectSettingsImpl DEFAULT = new ProjectSettingsImpl(new State());

  private State myState;

  public ProjectSettingsImpl(State state) {
    myState = state;
  }

  public IncrementalityType getIncrementalityType() {
    return myState.incrementalityType;
  }

  public CompileOrder getCompileOrder() {
    return myState.compileOrder;
  }

  public String[] getCompilerOptions() {
    List<String> list = new ArrayList<String>();

    if (myState.dynamics) {
      list.add("-language:dynamics");
    }

    if (myState.postfixOps) {
      list.add("-language:postfixOps");
    }

    if (myState.reflectiveCalls) {
      list.add("-language:reflectiveCalls");
    }

    if (myState.implicitConversions) {
      list.add("-language:implicitConversions");
    }

    if (myState.higherKinds) {
      list.add("-language:higherKinds");
    }

    if (myState.existentials) {
      list.add("-language:existentials");
    }

    if (myState.macros) {
      list.add("-language:experimental.macros");
    }

    if (!myState.warnings) {
      list.add("-nowarn");
    }

    if (myState.deprecationWarnings) {
      list.add("-deprecation");
    }

    if (myState.uncheckedWarnings) {
      list.add("-unchecked");
    }

    if (myState.featureWarnings) {
      list.add("-feature");
    }

    if (myState.optimiseBytecode) {
      list.add("-optimise");
    }

    if (myState.explainTypeErrors) {
      list.add("-explaintypes");
    }

    if (!myState.specialization) {
      list.add("-no-specialization");
    }

    if (myState.continuations) {
      list.add("-P:continuations:enable");
    }

    switch (myState.debuggingInfoLevel) {
      case None:
        list.add("-g:none");
        break;
      case Source:
        list.add("-g:source");
        break;
      case Line:
        list.add("-g:line");
        break;
      case Vars:
        list.add("-g:vars");
        break;
      case Notc:
        list.add("-g:notc");
    }

    for (String pluginPath : myState.plugins) {
      list.add("-Xplugin:" + FileUtil.toCanonicalPath(pluginPath));
    }

    list.addAll(Arrays.asList(myState.additionalCompilerOptions));

    return list.toArray(new String[list.size()]);
  }

  @NotNull
  @Override
  public ProjectSettingsImpl createCopy() {
    return new ProjectSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull ProjectSettingsImpl facetSettings) {
    // do nothing
  }

  public static class State {
    public IncrementalityType incrementalityType = IncrementalityType.IDEA;

    public CompileOrder compileOrder = CompileOrder.Mixed;

    public boolean dynamics;

    public boolean postfixOps;

    public boolean reflectiveCalls;

    public boolean implicitConversions;

    public boolean higherKinds;

    public boolean existentials;

    public boolean macros;

    public boolean warnings = true; //no -nowarn

    public boolean deprecationWarnings;

    public boolean uncheckedWarnings;

    public boolean featureWarnings;

    public boolean optimiseBytecode;

    public boolean explainTypeErrors;

    public boolean specialization = true; //no -no-specialization

    public boolean continuations;

    public DebuggingInfoLevel debuggingInfoLevel = DebuggingInfoLevel.Vars;

    // Why serialization doesn't work when elementTag is "option"?
    @Tag("parameters")
    @AbstractCollection(surroundWithTag = false, elementTag = "parameter")
    public String[] additionalCompilerOptions = new String[] {};

    @Tag("plugins")
    @AbstractCollection(surroundWithTag = false, elementTag = "plugin", elementValueAttribute = "path")
    public String[] plugins = new String[] {};
  }
}
