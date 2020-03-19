package org.jetbrains.plugins.scala.externalHighlighters;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectModelBuildableElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.ProjectTask;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskManager;
import com.intellij.task.ProjectTaskManager.Result;
import com.intellij.task.ProjectTaskNotification;
import com.intellij.task.impl.ProjectTaskManagerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.function.Supplier;

public abstract class DecoratePromiseProjectTaskManager extends ProjectTaskManager {

    private final ProjectTaskManager delegate;

    public DecoratePromiseProjectTaskManager(Project project) {
        super(project);
        this.delegate = new ProjectTaskManagerImpl(project);
    }

    @Override
    public Promise<Result> run(@NotNull ProjectTask projectTask) {
        return decorate(() -> delegate.run(projectTask));
    }

    @Override
    public Promise<Result> run(@NotNull ProjectTaskContext context, @NotNull ProjectTask projectTask) {
        return decorate(() -> delegate.run(context, projectTask));
    }

    @Override
    public Promise<Result> buildAllModules() {
        return decorate(() -> delegate.buildAllModules());
    }

    @Override
    public Promise<Result> rebuildAllModules() {
        return decorate(() -> delegate.rebuildAllModules());
    }

    @Override
    public Promise<Result> build(@NotNull Module... modules) {
        return decorate(() -> delegate.build(modules));
    }

    @Override
    public Promise<Result> rebuild(@NotNull Module... modules) {
        return decorate(() -> delegate.rebuild(modules));
    }

    @Override
    public Promise<Result> compile(@NotNull VirtualFile... files) {
        return decorate(() -> delegate.compile(files));
    }

    @Override
    public Promise<Result> build(@NotNull ProjectModelBuildableElement... buildableElements) {
        return decorate(() ->delegate.build(buildableElements));
    }

    @Override
    public Promise<Result> rebuild(@NotNull ProjectModelBuildableElement... buildableElements) {
        return decorate(() -> delegate.rebuild(buildableElements));
    }

    abstract protected Promise<Result> decorate(Supplier<Promise<Result>> supplier);

    @Override
    public ProjectTask createAllModulesBuildTask(boolean isIncrementalBuild, Project project) {
        return delegate.createAllModulesBuildTask(isIncrementalBuild, project);
    }

    @Override
    public ProjectTask createModulesBuildTask(Module module, boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies) {
        return delegate.createModulesBuildTask(module, isIncrementalBuild, includeDependentModules, includeRuntimeDependencies);
    }

    @Override
    public ProjectTask createModulesBuildTask(Module[] modules, boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies) {
        return delegate.createModulesBuildTask(modules, isIncrementalBuild, includeDependentModules, includeRuntimeDependencies);
    }

    @Override
    public ProjectTask createBuildTask(boolean isIncrementalBuild, ProjectModelBuildableElement... artifacts) {
        return delegate.createBuildTask(isIncrementalBuild, artifacts);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void run(@NotNull ProjectTask projectTask, @Nullable ProjectTaskNotification callback) {
        delegate.run(projectTask, callback);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void run(@NotNull ProjectTaskContext context, @NotNull ProjectTask projectTask, @Nullable ProjectTaskNotification callback) {
        delegate.run(context, projectTask, callback);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void buildAllModules(@Nullable ProjectTaskNotification callback) {
        delegate.buildAllModules(callback);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void rebuildAllModules(@Nullable ProjectTaskNotification callback) {
        delegate.rebuildAllModules(callback);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void build(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback) {
        delegate.build(modules, callback);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void rebuild(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback) {
        delegate.rebuild(modules, callback);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void compile(@NotNull VirtualFile[] files, @Nullable ProjectTaskNotification callback) {
        delegate.compile(files, callback);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void build(@NotNull ProjectModelBuildableElement[] buildableElements, @Nullable ProjectTaskNotification callback) {
        delegate.build(buildableElements, callback);
    }

    @Override
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
    @Deprecated
    public void rebuild(@NotNull ProjectModelBuildableElement[] buildableElements, @Nullable ProjectTaskNotification callback) {
        delegate.rebuild(buildableElements, callback);
    }
}
