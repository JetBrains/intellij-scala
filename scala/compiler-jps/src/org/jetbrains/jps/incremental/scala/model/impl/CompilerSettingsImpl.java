package org.jetbrains.jps.incremental.scala.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.CompilerSettings;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.plugins.scala.compiler.data.CompileOrder;
import org.jetbrains.plugins.scala.compiler.data.SbtIncrementalOptions;
import org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompilerSettingsImpl extends JpsElementBase<CompilerSettingsImpl> implements CompilerSettings {
  public static final CompilerSettingsImpl DEFAULT = new CompilerSettingsImpl(new ScalaCompilerSettingsState());

  private final ScalaCompilerSettingsState myState;

  public CompilerSettingsImpl(ScalaCompilerSettingsState state) {
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
  public String[] getCompilerOptionsAsStrings(boolean forScala3Compiler) {
    List<String> list = new ArrayList<>();

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

    if (myState.experimental) {
      list.add("-Xexperimental");
    }

    //TODO: SCL-16881 Support "Debugging info level" for dotty
    if (!forScala3Compiler) {
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
    }

    for (String pluginPath : myState.plugins) {
      list.add("-Xplugin:" + FileUtil.toCanonicalPath(pluginPath));
    }

    list.addAll(Arrays.asList(myState.additionalCompilerOptions));

    return list.toArray(new String[0]);
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
}
