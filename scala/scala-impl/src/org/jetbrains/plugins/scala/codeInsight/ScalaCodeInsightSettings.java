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

    public static final int MIN_PRESENTATION_LENGTH = Byte.MAX_VALUE + Byte.MIN_VALUE;
    public static final int MAX_PRESENTATION_LENGTH = Byte.MAX_VALUE - Byte.MIN_VALUE;

    public static ScalaCodeInsightSettings getInstance() {
        return ServiceManager.getService(ScalaCodeInsightSettings.class);
    }

    private boolean showFunctionReturnType = true;
    private boolean showPropertyType = false;
    private boolean showLocalVariableType = false;

    private int presentationLength = 50;

    private boolean showForObviousTypes = false;

    public boolean isShowTypeHints() {
        return showFunctionReturnType ||
                showPropertyType ||
                showLocalVariableType;
    }

    public boolean isShowFunctionReturnType() {
        return showFunctionReturnType;
    }

    public Getter<Boolean> showFunctionReturnTypeGetter() {
        return this::isShowFunctionReturnType;
    }

    private void setShowFunctionReturnType(boolean showFunctionReturnType) {
        this.showFunctionReturnType = showFunctionReturnType;
    }

    public Setter<Boolean> showFunctionReturnTypeSetter() {
        return this::setShowFunctionReturnType;
    }

    public boolean isShowPropertyType() {
        return showPropertyType;
    }

    public Getter<Boolean> showPropertyTypeGetter() {
        return this::isShowPropertyType;
    }

    private void setShowPropertyType(boolean showPropertyType) {
        this.showPropertyType = showPropertyType;
    }

    public Setter<Boolean> showPropertyTypeSetter() {
        return this::setShowPropertyType;
    }

    public boolean isShowLocalVariableType() {
        return showLocalVariableType;
    }

    public Getter<Boolean> showLocalVariableTypeGetter() {
        return this::isShowLocalVariableType;
    }

    private void setShowLocalVariableType(boolean showLocalVariableType) {
        this.showLocalVariableType = showLocalVariableType;
    }

    public Setter<Boolean> showLocalVariableTypeSetter() {
        return this::setShowLocalVariableType;
    }

    public int getPresentationLength() {
        return presentationLength;
    }

    public Getter<Integer> presentationLengthGetter() {
        return this::getPresentationLength;
    }

    private void setPresentationLength(int presentationLength) {
        this.presentationLength = presentationLength;
    }

    public Setter<Integer> presentationLengthSetter() {
        return this::setPresentationLength;
    }


    public boolean isShowForObviousTypes() {
        return showForObviousTypes;
    }

    public Getter<Boolean> showForObviousTypesGetter() {
        return this::isShowForObviousTypes;
    }

    public void setShowForObviousTypes(boolean showForObviousTypes) {
        this.showForObviousTypes = showForObviousTypes;
    }

    public Setter<Boolean> showForObviousTypesSetter() {
        return this::setShowForObviousTypes;
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

