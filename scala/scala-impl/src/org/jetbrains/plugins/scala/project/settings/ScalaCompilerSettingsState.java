package org.jetbrains.plugins.scala.project.settings;

import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.jps.incremental.scala.data.SbtIncrementalOptions;
import org.jetbrains.plugins.scala.project.CompileOrder;
import org.jetbrains.plugins.scala.project.DebuggingInfoLevel;

import java.util.Arrays;

/**
 * @author Pavel Fatin
 */
public class ScalaCompilerSettingsState {
  public CompileOrder compileOrder = CompileOrder.Mixed;

  public boolean nameHashing = SbtIncrementalOptions.Default().nameHashing();

  public boolean recompileOnMacroDef = SbtIncrementalOptions.Default().recompileOnMacroDef();

  public int transitiveStep = SbtIncrementalOptions.Default().transitiveStep();

  public double recompileAllFraction = SbtIncrementalOptions.Default().recompileAllFraction();

  public boolean dynamics = false;

  public boolean postfixOps = false;

  public boolean reflectiveCalls = false;

  public boolean implicitConversions = false;

  public boolean higherKinds = false;

  public boolean existentials = false;

  public boolean macros = false;

  public boolean experimental = false;

  public boolean warnings = true;

  public boolean deprecationWarnings = false;

  public boolean uncheckedWarnings = false;

  public boolean featureWarnings = false;

  public boolean optimiseBytecode = false;

  public boolean explainTypeErrors = false;

  public boolean specialization = true;

  public boolean continuations = false;

  public DebuggingInfoLevel debuggingInfoLevel = DebuggingInfoLevel.Vars;

  public boolean hasOption(String optionKey) {
    return ArrayUtil.contains(optionKey, additionalCompilerOptions);
  }

  // Why serialization doesn't work when elementTag is "option"?
  @Tag("parameters")
  @AbstractCollection(surroundWithTag = false, elementTag = "parameter")
  public String[] additionalCompilerOptions = new String[] {};

  @Tag("plugins")
  @AbstractCollection(surroundWithTag = false, elementTag = "plugin", elementValueAttribute = "path")
  public String[] plugins = new String[] {};

  @Override
  public boolean equals(Object o) {
    ScalaCompilerSettingsState that = (ScalaCompilerSettingsState) o;

    return
        compileOrder == that.compileOrder &&
        nameHashing == that.nameHashing &&
        recompileOnMacroDef == that.recompileOnMacroDef &&
        transitiveStep == that.transitiveStep &&
        recompileAllFraction == that.recompileAllFraction &&
        dynamics == that.dynamics &&
        postfixOps == that.postfixOps &&
        reflectiveCalls == that.reflectiveCalls &&
        implicitConversions == that.implicitConversions &&
        higherKinds == that.higherKinds &&
        existentials == that.existentials &&
        macros == that.macros &&
        experimental == that.experimental &&
        warnings == that.warnings &&
        deprecationWarnings == that.deprecationWarnings &&
        uncheckedWarnings == that.uncheckedWarnings &&
        featureWarnings == that.featureWarnings &&
        optimiseBytecode == that.optimiseBytecode &&
        explainTypeErrors == that.explainTypeErrors &&
        specialization == that.specialization &&
        continuations == that.continuations &&
        Arrays.equals(additionalCompilerOptions, that.additionalCompilerOptions) &&
        Arrays.equals(plugins, that.plugins) &&
        debuggingInfoLevel == that.debuggingInfoLevel;
  }
}
