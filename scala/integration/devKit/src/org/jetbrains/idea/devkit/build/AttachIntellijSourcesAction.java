package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.JavaVfsSourceRootDetectionUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AttachIntellijSourcesAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() != null && !event.getProject().isDisposed())
            attachIJSources(event.getProject());
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }

    private static final Logger LOG = Logger.getInstance(IntellijSourcesAttachListener.class);

    private static final Predicate<String> JAR_PATTERN = Pattern.compile("^sources\\.(zip|jar)$").asPredicate();

    public static void attachIJSources(@NotNull Project project) {
        if (project.isDisposed()) return;

        Application application            = ApplicationManager.getApplication();

        Set<LibraryOrderEntry> ijLibraries = application.runReadAction(getSourcelessIJLibraries(project));
        if (ijLibraries.isEmpty()) {
            LOG.info("No IJ libraries without sources found in current project");
            return;
        }
        LOG.info("Found " + ijLibraries.size() + " IJ libraries without sources");

        Optional<VirtualFile> ideaInstallationFolder = application.runReadAction(getLibraryRoot(ijLibraries));
        if (!ideaInstallationFolder.isPresent()) {
            LOG.info("Couldn't find IDEA installation folder");
            return;
        }
        LOG.info("Found IDEA installation folder at " + ideaInstallationFolder.get());

        Optional<VirtualFile> maybeSourcesZip = ideaInstallationFolder.flatMap(AttachIntellijSourcesAction::findSourcesZip);
        if (!maybeSourcesZip.isPresent()) {
            LOG.info("Couldn't find IDEA sources in installation folder: " + ideaInstallationFolder.get());
            return;
        }

        new Task.Backgroundable(project, DevkitBundle.message("attaching.idea.sources"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                LOG.info("Sources archive found: " + maybeSourcesZip.get().getCanonicalPath());

                Collection<VirtualFile> roots = JavaVfsSourceRootDetectionUtil.suggestRoots(maybeSourcesZip.get(), indicator);

                ApplicationManager.getApplication().invokeLater(() -> {
                    ijLibraries.stream()
                            .map(LibraryOrderEntry::getLibrary)
                            .filter(LibraryEx.class::isInstance)
                            .map(LibraryEx.class::cast)
                            .forEach(library -> application.runWriteAction(addLibrary(library, roots, indicator)));
                    LOG.info("Finished attaching IDEA sources");
                });
            }

            private Runnable addLibrary(LibraryEx library, Collection<VirtualFile> roots, ProgressIndicator indicator) {
                return () -> {
                    if (library.isDisposed()) return;
                    indicator.checkCanceled();
                    Library.ModifiableModel model = library.getModifiableModel();
                    roots.forEach(file -> model.addRoot(file, OrderRootType.SOURCES));
                    model.commit();
                };
            }
        }.queue();
    }


    @NotNull
    private static Computable<Optional<VirtualFile>> getLibraryRoot(@NotNull Set<LibraryOrderEntry> sourceless) {
        return () -> sourceless
                .stream()
                .map(entry -> entry.getFiles(OrderRootType.CLASSES))
                .map(AttachIntellijSourcesAction::findIJInstallationDirectory)
                .filter(Objects::nonNull)
                .findFirst();
    }

    @NotNull
    private static Computable<Set<LibraryOrderEntry>> getSourcelessIJLibraries(@NotNull Project project) {
        return () -> Arrays
                .stream(PackageIndex.getInstance(project).getDirectoriesByPackageName("com.intellij", false))
                .flatMap(file -> getLibraryOrderEntries(project, file))
                .filter(AttachIntellijSourcesAction::isSourceless)
                .collect(Collectors.toSet());
    }

    private static Computable<Set<VirtualFile>> detectSourceRoots(@NotNull VirtualFile sourcesZip) {
        return null;
    }

    @NotNull
    private static Stream<LibraryOrderEntry> getLibraryOrderEntries(@NotNull Project project, @NotNull VirtualFile file) {
        return !file.getUrl().contains(".jar!") ?
                Stream.empty() :
                ProjectFileIndex.SERVICE.getInstance(project).getOrderEntriesForFile(file)
                        .stream()
                        .filter(LibraryOrderEntry.class::isInstance)
                        .map(LibraryOrderEntry.class::cast);
    }

    private static boolean isSourceless(@NotNull LibraryOrderEntry entry) {
        Library library = entry.getLibrary();
        return library != null && library.getUrls(OrderRootType.SOURCES).length == 0;
    }

    @Nullable
    private static VirtualFile findIJInstallationDirectory(@NotNull VirtualFile[] jarFiles) {
        VirtualFile jarFile = findJarFile(jarFiles);
        VirtualFile dir = VfsUtil.getVirtualFileForJar(jarFile);

        while (dir != null) {
            if (isIJInstallationDir(dir))  {
                break;
            } else {
                dir = dir.getParent();
            }
        }
        return dir;
    }

    private static boolean isIJInstallationDir(@NotNull VirtualFile dir) {
        return (dir.findChild("bin") != null) &&
                (dir.findChild("lib") != null) &&
                (dir.findChild("plugins") != null);
    }

    @Nullable
    private static VirtualFile findJarFile(@NotNull VirtualFile[] jarFiles) {
        VirtualFile firstFile = safeGet(jarFiles, 0);

        return firstFile == null || isToolsJar(firstFile.getCanonicalPath()) ?
                safeGet(jarFiles, 1) :
                firstFile;
    }

    @Nullable
    private static VirtualFile safeGet(@NotNull VirtualFile[] files, int index) {
        return files.length > index ?
                files[index] :
                null;
    }

    private static boolean isToolsJar(@Nullable String path) {
        return path != null && path.contains("tools.jar");
    }

    @NotNull
    private static Optional<VirtualFile> findSourcesZip(@NotNull VirtualFile root) {
        VirtualFile[] result = {null};
        try {
            VirtualFileManager fileManager = VirtualFileManager.getInstance();
            VfsUtilCore.visitChildrenRecursively(
                    root,
                    new VirtualFileVisitor() {
                        @NotNull
                        @Override
                        public Result visitFileEx(@NotNull VirtualFile file) {
                            if (JAR_PATTERN.test(file.getName())) {
                                result[0] = fileManager.findFileByUrl("jar://" + file.getCanonicalPath() + "!/");
                                throw new RuntimeException();
                            } else {
                                return VirtualFileVisitor.CONTINUE;
                            }
                        }
                    });
        } catch (Exception ignored) {
        }
        return Optional.ofNullable(result[0]);
    }
}
