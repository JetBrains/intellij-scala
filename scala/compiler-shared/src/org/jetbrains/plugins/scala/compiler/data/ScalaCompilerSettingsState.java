package org.jetbrains.plugins.scala.compiler.data;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.Arrays;
import java.util.Objects;

/**
 * NOTE, the class has some tests in `org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsTest`
 */
public final class ScalaCompilerSettingsState {
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
    public boolean strict = false;
    public boolean optimiseBytecode = false;
    public boolean explainTypeErrors = false;
    public boolean specialization = true;
    public boolean continuations = false;
    public DebuggingInfoLevel debuggingInfoLevel = DebuggingInfoLevel.Vars;

    // Why serialization doesn't work when elementTag is "option"?
    @Tag("parameters")
    @XCollection(elementName = "parameter")
    public String[] additionalCompilerOptions = new String[]{};

    @Tag("plugins")
    @XCollection(elementName = "plugin", valueAttributeName = "path")
    public String[] plugins = new String[]{};

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScalaCompilerSettingsState that = (ScalaCompilerSettingsState) o;
        return nameHashing == that.nameHashing &&
                recompileOnMacroDef == that.recompileOnMacroDef &&
                transitiveStep == that.transitiveStep &&
                Double.compare(that.recompileAllFraction, recompileAllFraction) == 0 &&
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
                strict == that.strict &&
                optimiseBytecode == that.optimiseBytecode &&
                explainTypeErrors == that.explainTypeErrors &&
                specialization == that.specialization &&
                continuations == that.continuations &&
                compileOrder.equals(that.compileOrder) &&
                debuggingInfoLevel.equals(that.debuggingInfoLevel) &&
                Arrays.equals(additionalCompilerOptions, that.additionalCompilerOptions) &&
                Arrays.equals(plugins, that.plugins);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                compileOrder,
                nameHashing,
                recompileOnMacroDef,
                transitiveStep,
                recompileAllFraction,
                dynamics,
                postfixOps,
                reflectiveCalls,
                implicitConversions,
                higherKinds,
                existentials,
                macros,
                experimental,
                warnings,
                deprecationWarnings,
                uncheckedWarnings,
                featureWarnings,
                strict,
                optimiseBytecode,
                explainTypeErrors,
                specialization,
                continuations,
                debuggingInfoLevel
        );
        result = 31 * result + Arrays.hashCode(additionalCompilerOptions);
        result = 31 * result + Arrays.hashCode(plugins);
        return result;
    }
}
