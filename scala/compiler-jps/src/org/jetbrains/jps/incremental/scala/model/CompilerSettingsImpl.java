package org.jetbrains.jps.incremental.scala.model;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.compiler.CompileOrder;
import org.jetbrains.plugins.scala.compiler.IncrementalityType;
import org.jetbrains.plugins.scala.compiler.data.SbtIncrementalOptions;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class CompilerSettingsImpl extends JpsElementBase<CompilerSettingsImpl> implements CompilerSettings {
  public static final CompilerSettingsImpl DEFAULT = new CompilerSettingsImpl(new State());

  private State myState;

  public CompilerSettingsImpl(State state) {
    myState = state;
  }

  @Override
  public CompileOrder getCompileOrder() {
    return myState.compileOrder;
  }

  @Override
  public SbtIncrementalOptions getSbtIncrementalOptions() {
    return new SbtIncrementalOptions(myState.nameHashing, myState.recompileOnMacroDef, myState.transitiveStep, myState.recompileAllFraction);
  }

  @Override
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

    if (myState.scala2Compat) {
      list.add("-language:Scala2Compat");
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

    if (myState.strictMode) {
      list.add("-strict");
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

    if (myState.experimental) {
      list.add("-Xexperimental");
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
      case Notailcalls:
        list.add("-g:notailcalls");
    }

    for (String pluginPath : myState.plugins) {
      list.add("-Xplugin:" + FileUtil.toCanonicalPath(pluginPath));
    }

    list.addAll(Arrays.asList(myState.additionalCompilerOptions));

    return list.toArray(new String[list.size()]);
  }

  @NotNull
  @Override
  public CompilerSettingsImpl createCopy() {
    return new CompilerSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull CompilerSettingsImpl compilerSettings) {
    // do nothing
  }

  public static class State {
    public IncrementalityType incrementalityType = IncrementalityType.IDEA;

    public CompileOrder compileOrder = CompileOrder.Mixed;

    public boolean nameHashing = SbtIncrementalOptions.Default().nameHashing();

    public boolean recompileOnMacroDef = SbtIncrementalOptions.Default().recompileOnMacroDef();

    public int transitiveStep = SbtIncrementalOptions.Default().transitiveStep();

    public double recompileAllFraction = SbtIncrementalOptions.Default().recompileAllFraction();

    public boolean dynamics;

    public boolean postfixOps;

    public boolean reflectiveCalls;

    public boolean implicitConversions;

    public boolean higherKinds;

    public boolean existentials;

    public boolean scala2Compat;

    public boolean macros;

    public boolean experimental;

    public boolean warnings = true; //no -nowarn

    public boolean deprecationWarnings;

    public boolean uncheckedWarnings;

    public boolean featureWarnings;

    public boolean strictMode;

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
