package org.jetbrains.plugins.scala.packageSearch;

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.rd.LifetimeDisposableExKt;
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider;
import com.jetbrains.rd.util.lifetime.Lifetime;
import com.jetbrains.rd.util.reactive.ISource;
import com.jetbrains.rd.util.reactive.Signal;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SbtModuleChangesSignalProvider implements ModuleChangesSignalProvider {
    @NotNull
    @Override
    public ISource<Unit> listenToModuleChanges(@NotNull Project project, @NotNull Lifetime lifetime) {

        Signal<Unit> signal = Signal.Companion.Void();
        ProjectDataImportListener sbtProjectDataImportListener = (@Nullable String s) -> {
            signal.fire(Unit.INSTANCE);
        };
        project.getMessageBus().connect(LifetimeDisposableExKt.createNestedDisposable(lifetime, "lifetimeToDisposable")).subscribe(
                sbtProjectDataImportListener.TOPIC,
                sbtProjectDataImportListener
        );
        return signal;
    }
}
