package org.jetbrains.plugins.scala.project;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.Arrays;

/**
 * @author Pavel Fatin
 */
public class ScalaCompilerSettingsState {
  public IncrementalityType incrementalityType = IncrementalityType.IDEA;

  public CompileOrder compileOrder = CompileOrder.Mixed;

  public boolean macros = false;

  public boolean dynamics = false;

  public boolean postfixOps = false;

  public boolean reflectiveCalls = false;

  public boolean implicitConversions = false;

  public boolean higherKinds = false;

  public boolean existentials = false;

  public boolean warnings = true;

  public boolean deprecationWarnings = false;

  public boolean uncheckedWarnings = false;

  public boolean featureWarnings = false;

  public boolean optimiseBytecode = false;

  public boolean explainTypeErrors = false;

  public boolean continuations = false;

  public DebuggingInfoLevel debuggingInfoLevel = DebuggingInfoLevel.Vars;

  public String additionalCompilerOptions = "";

  @Tag("plugins")
  @AbstractCollection(surroundWithTag = false, elementTag = "plugin", elementValueAttribute = "url")
  public String[] plugins = new String[] {};

  @Override
  public boolean equals(Object o) {
    ScalaCompilerSettingsState that = (ScalaCompilerSettingsState) o;

    return
        macros == that.macros &&
        dynamics == that.dynamics &&
        postfixOps == that.postfixOps &&
        reflectiveCalls == that.reflectiveCalls &&
        implicitConversions == that.implicitConversions &&
        higherKinds == that.higherKinds &&
        existentials == that.existentials &&
        continuations == that.continuations &&
        deprecationWarnings == that.deprecationWarnings &&
        explainTypeErrors == that.explainTypeErrors &&
        optimiseBytecode == that.optimiseBytecode &&
        uncheckedWarnings == that.uncheckedWarnings &&
        featureWarnings == that.featureWarnings &&
        warnings == that.warnings &&
        additionalCompilerOptions.equals(that.additionalCompilerOptions) &&
        compileOrder == that.compileOrder &&
        debuggingInfoLevel == that.debuggingInfoLevel &&
        Arrays.equals(plugins, that.plugins);
  }
}
