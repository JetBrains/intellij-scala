package org.jetbrains.plugins.scala.worksheet.actions;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.nio.file.Path;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public abstract class ModuleDelegate implements Module {

    private final Module module;

    public ModuleDelegate(Module module) {
        this.module = module;
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull MessageBus getMessageBus() {
        return module.getMessageBus();
    }

    @Override
    public @Nullable VirtualFile getModuleFile() {
        return module.getModuleFile();
    }

    @Override
    public @NotNull Path getModuleNioFile() {
        return module.getModuleNioFile();
    }

    @Override
    public @NotNull Project getProject() {
        return module.getProject();
    }

    @Override
    public @NotNull @NlsSafe String getName() {
        return module.getName();
    }

    @Override
    public boolean isDisposed() {
        return module.isDisposed();
    }

    @Override
    public boolean isLoaded() {
        return module.isLoaded();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setOption(@NotNull String key, @Nullable String value) {
        module.setOption(key, value);
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NonNls @Nullable String getOptionValue(@NotNull String key) {
        return module.getOptionValue(key);
    }

    @Override
    public @NotNull GlobalSearchScope getModuleScope() {
        return module.getModuleScope();
    }

    @Override
    public @NotNull GlobalSearchScope getModuleScope(boolean includeTests) {
        return module.getModuleScope(includeTests);
    }

    @Override
    public @NotNull GlobalSearchScope getModuleWithLibrariesScope() {
        return module.getModuleWithLibrariesScope();
    }

    @Override
    public @NotNull GlobalSearchScope getModuleWithDependenciesScope() {
        return module.getModuleWithDependenciesScope();
    }

    @Override
    public @NotNull GlobalSearchScope getModuleContentScope() {
        return module.getModuleContentScope();
    }

    @Override
    public @NotNull GlobalSearchScope getModuleContentWithDependenciesScope() {
        return module.getModuleContentWithDependenciesScope();
    }

    @Override
    public @NotNull GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
        return module.getModuleWithDependenciesAndLibrariesScope(includeTests);
    }

    @Override
    public @NotNull GlobalSearchScope getModuleWithDependentsScope() {
        return module.getModuleWithDependentsScope();
    }

    @Override
    public @NotNull GlobalSearchScope getModuleTestsWithDependentsScope() {
        return module.getModuleTestsWithDependentsScope();
    }

    @Override
    public @NotNull GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
        return module.getModuleRuntimeScope(includeTests);
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> T getComponent(@NotNull Class<T> interfaceClass) {
        return module.getComponent(interfaceClass);
    }

    @Override
    public boolean isInjectionForExtensionSupported() {
        return module.isInjectionForExtensionSupported();
    }

    @Override
    public @NotNull Condition<?> getDisposed() {
        return module.getDisposed();
    }

    @Override
    public <T> T getService(@NotNull Class<T> serviceClass) {
        return module.getService(serviceClass);
    }

    @Override
    public <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass, @NotNull Object key, @NotNull PluginId pluginId) {
        return module.instantiateClassWithConstructorInjection(aClass, key, pluginId);
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
        return module.createError(error, pluginId);
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId) {
        return module.createError(message, pluginId);
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull @NonNls String message,
                                                 @Nullable Throwable error,
                                                 @NotNull PluginId pluginId,
                                                 @Nullable Map<String, String> attachments) {
        return module.createError(message, error, pluginId, attachments);
    }

    @Override
    public @NotNull <T> Class<T> loadClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
        return module.loadClass(className, pluginDescriptor);
    }

    @Override
    public @NotNull ActivityCategory getActivityCategory(boolean isExtension) {
        return module.getActivityCategory(isExtension);
    }

    @Override
    public void dispose() {
        module.dispose();
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
        return module.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
        module.putUserData(key, value);
    }
}
