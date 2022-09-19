package org.jetbrains.sbt.project.template.wizard.kotlin_interop;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.observable.properties.ObservableMutableProperty;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.JdkComboBoxKt;
import com.intellij.openapi.roots.ui.configuration.SdkListItem;
import com.intellij.ui.dsl.builder.Cell;
import com.intellij.ui.dsl.builder.Row;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class JdkComboBoxKt_Interop {

    /**
     * Wrapper method to avoid StackOverflow in implicit search (https://github.com/scala/bug/issues/11775)
     */
    public static Cell<JdkComboBox> sdkComboBox(
            Row row,
            WizardContext context,
            ObservableMutableProperty<Sdk> sdkProperty,
            String sdkPropertyId,
            Function1<SdkTypeId, Boolean> sdkTypeFilter,
            Function1<Sdk, Boolean> sdkFilter,
            Function1<SdkListItem.SuggestedItem, Boolean> suggestedSdkItemFilter,
            Function1<SdkTypeId, Boolean> creationSdkTypeFilter,
            Function1<Sdk, Unit> onNewSdkAdded
    ) {
        return JdkComboBoxKt.sdkComboBox(
                row,
                context,
                sdkProperty,
                sdkPropertyId,
                sdkTypeFilter,
                sdkFilter,
                suggestedSdkItemFilter,
                creationSdkTypeFilter,
                onNewSdkAdded
        );
    }
}
