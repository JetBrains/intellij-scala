package org.jetbrains.jps.incremental.scala.model.impl;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.CompilerSettings;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.plugins.scala.compiler.data.CompileOrder;
import org.jetbrains.plugins.scala.compiler.data.SbtIncrementalOptions;
import org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState;
import org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsStateBuilder;
import scala.collection.immutable.Seq;
import scala.jdk.CollectionConverters$;

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
  public List<String> getCompilerOptionsAsStrings(boolean forScala3Compiler) {
    Seq<String> stringsSeq = ScalaCompilerSettingsStateBuilder.getOptionsAsStrings(myState, forScala3Compiler, true);
    return CollectionConverters$.MODULE$.SeqHasAsJava(stringsSeq).asJava();
  }
}
