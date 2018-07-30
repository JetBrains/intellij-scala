package org.jetbrains.plugins.scala.codeInsight;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Setter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
        name = "ScalaCodeInsightSettings",
        storages = {@Storage("scala_code_insight_settings.xml")}
)
public class ScalaCodeInsightSettings implements PersistentStateComponent<ScalaCodeInsightSettings> {

    public static final int MIN_PRESENTATION_LENGTH = 1;
    public static final int MAX_PRESENTATION_LENGTH = Byte.MAX_VALUE;

    public static ScalaCodeInsightSettings getInstance() {
        return ServiceManager.getService(ScalaCodeInsightSettings.class);
    }

    //private fields are not serialized
    public boolean showFunctionReturnType = true;
    public boolean showPropertyType = false;
    public boolean showLocalVariableType = false;

    public int presentationLength = 50;

    public boolean showObviousType = false;

    public Getter<Boolean> showFunctionReturnTypeGetter() {
        return () -> showFunctionReturnType;
    }

    public Setter<Boolean> showFunctionReturnTypeSetter() {
        return value -> showFunctionReturnType = value;
    }

    public Getter<Boolean> showPropertyTypeGetter() {
        return () -> showPropertyType;
    }

    public Setter<Boolean> showPropertyTypeSetter() {
        return value -> showPropertyType = value;
    }

    public Getter<Boolean> showLocalVariableTypeGetter() {
        return () -> showLocalVariableType;
    }

    public Setter<Boolean> showLocalVariableTypeSetter() {
        return value -> showLocalVariableType = value;
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

