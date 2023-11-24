package org.jetbrains.plugins.scala.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Setter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
        name = "ScalaCodeInsightSettings",
        storages = {@Storage("scala_code_insight_settings.xml")},
        reportStatistic = true,
        category = SettingsCategory.CODE
)
public class ScalaCodeInsightSettings implements PersistentStateComponent<ScalaCodeInsightSettings> {

    public static final boolean SHOW_PARAMETER_NAMES_DEFAULT = false;
    public static final boolean SHOW_METHOD_RESULT_TYPE_DEFAULT = true;
    public static final boolean SHOW_MEMBER_VARIABLE_TYPE_DEFAULT = false;
    public static final boolean SHOW_LOCAL_VARIABLE_TYPE_DEFAULT = false;
    public static final boolean SHOW_METHOD_CHAIN_INLAY_HINTS_DEFAULT = true;
    public static final boolean ALIGN_METHOD_CHAIN_INLAY_HINTS_DEFAULT = true;
    public static final int UNIQUE_TYPES_TO_SHOW_METHOD_CHAINS_DEFAULT = 3;
    public static final int PRESENTATION_LENGTH_DEFAULT = 45;
    public static final boolean SHOW_OBVIOUS_TYPE_DEFAULT = false;
    public static final boolean PRESERVE_INDENTS_DEFAULT = true;
    public static final boolean SHOW_RANGE_HINTS_FOR_TO_AND_UNTIL_DEFAULT = true;
    public static final boolean SHOW_EXCLUSIVE_RANGE_HINT_DEFAULT = true;

    public static final int MIN_PRESENTATION_LENGTH = 1;
    public static final int MAX_PRESENTATION_LENGTH = Byte.MAX_VALUE;

    public static ScalaCodeInsightSettings getInstance() {
        return ApplicationManager.getApplication().getService(ScalaCodeInsightSettings.class);
    }

    //private fields are not serialized
    public boolean showParameterNames = SHOW_PARAMETER_NAMES_DEFAULT;
    // TODO Rename to "showMethodResultType" (setting format upgrade is required)
    public boolean showFunctionReturnType = SHOW_METHOD_RESULT_TYPE_DEFAULT;
    // TODO Rename to "showMemberVariableType" (setting format upgrade is required)
    public boolean showPropertyType = SHOW_MEMBER_VARIABLE_TYPE_DEFAULT;
    public boolean showLocalVariableType = SHOW_LOCAL_VARIABLE_TYPE_DEFAULT;
    public boolean showMethodChainInlayHints = SHOW_METHOD_CHAIN_INLAY_HINTS_DEFAULT;
    public boolean alignMethodChainInlayHints = ALIGN_METHOD_CHAIN_INLAY_HINTS_DEFAULT;
    public int uniqueTypesToShowMethodChains = UNIQUE_TYPES_TO_SHOW_METHOD_CHAINS_DEFAULT;
    public int presentationLength = PRESENTATION_LENGTH_DEFAULT;
    public boolean showObviousType = SHOW_OBVIOUS_TYPE_DEFAULT;
    public boolean preserveIndents = PRESERVE_INDENTS_DEFAULT;
    public boolean showRangeHintsForToAndUntil = SHOW_RANGE_HINTS_FOR_TO_AND_UNTIL_DEFAULT;
    public boolean showExclusiveRangeHint = SHOW_EXCLUSIVE_RANGE_HINT_DEFAULT;


    public Getter<Boolean> showParameterNamesGetter() {
        return () -> showParameterNames;
    }

    public Setter<Boolean> showParameterNamesSetter() {
        return value -> showParameterNames = value;
    }

    public Getter<Boolean> showMethodResultTypeGetter() {
        return () -> showFunctionReturnType;
    }

    public Setter<Boolean> showMethodResultTypeSetter() {
        return value -> showFunctionReturnType = value;
    }

    public Getter<Boolean> showMemberVariableTypeGetter() {
        return () -> showPropertyType;
    }

    public Setter<Boolean> showMemberVariableSetter() {
        return value -> showPropertyType = value;
    }

    public Getter<Boolean> showLocalVariableTypeGetter() {
        return () -> showLocalVariableType;
    }

    public Setter<Boolean> showLocalVariableTypeSetter() {
        return value -> showLocalVariableType = value;
    }

    public Getter<Boolean> showMethodChainInlayHintsGetter() {
        return () -> showMethodChainInlayHints;
    }

    public Setter<Boolean> showMethodChainInlayHintsSetter() {
        return value -> showMethodChainInlayHints = value;
    }

    public Getter<Boolean> alignMethodChainInlayHintsGetter() {
        return () -> alignMethodChainInlayHints;
    }

    public Setter<Boolean> alignMethodChainInlayHintsSetter() {
        return value -> alignMethodChainInlayHints = value;
    }

    public Getter<Integer> uniqueTypesToShowMethodChainsGetter() {
        return () -> uniqueTypesToShowMethodChains;
    }

    public Setter<Integer> uniqueTypesToShowMethodChainsSetter() {
        return value -> uniqueTypesToShowMethodChains = value;
    }

    public Getter<Integer> presentationLengthGetter() {
        return () -> presentationLength;
    }

    public Setter<Integer> presentationLengthSetter() {
        return value -> presentationLength = value;
    }

    public Getter<Boolean> showObviousTypeGetter() {
        return () -> showObviousType;
    }

    public Setter<Boolean> showObviousTypeSetter() {
        return value -> showObviousType = value;
    }

    public Getter<Boolean> preserveIndentsGetter() {
        return () -> preserveIndents;
    }

    public Setter<Boolean> preserveIndentsSetter() {
        return value -> preserveIndents = value;
    }

    public Getter<Boolean> showRangeHintsForToAndUntilGetter() {
        return () -> showRangeHintsForToAndUntil;
    }

    public Setter<Boolean> showRangeHintsForToAndUntilSetter() {
        return value -> showRangeHintsForToAndUntil = value;
    }


    public Getter<Boolean> showExclusiveRangeHintDefaultGetter() {
        return () -> showExclusiveRangeHint;
    }

    public Setter<Boolean> showExclusiveRangeHintDefaultSetter() {
        return value -> showExclusiveRangeHint = value;
    }

    @NotNull
    @Override
    public ScalaCodeInsightSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ScalaCodeInsightSettings settings) {
        XmlSerializerUtil.copyBean(settings, this);
    }
}

