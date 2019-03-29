package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
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
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.*;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IntelliJSourcesAttachListener extends ExternalSystemTaskNotificationListenerAdapter {

    private static final Logger LOG = Logger.getInstance(IntelliJSourcesAttachListener.class);

    private static final Predicate<String> PROJECT_NAME_PATTERN = Pattern.compile("^scalaUltimate|scalaCommunity|((scala-plugin-for-ultimate|intellij-scala)(_\\d+)?)$").asPredicate();
    private static final Predicate<String> JAR_PATTERN = Pattern.compile("^sources\\.(zip|jar)$").asPredicate();
    private static final Predicate<String> DIRECTORY_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$").asPredicate();

    private final static int MAX_RETRIES = 3;
    private final static int DELAY = 10000;

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        Project project = id.findProject();
        if (project == null) return;

        Application application = ApplicationManager.getApplication();
        Runnable activity = doAttach(application, project, 0);
        application.invokeLater(activity);

        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(
                ProjectDataImportListener.TOPIC,
                path -> {
                    doAttach(application, project, 0).run();
                    connection.disconnect();
                });
    }

    @NotNull
    private static Runnable doAttach(@NotNull Application application,
                                     @NotNull Project project,
                                     int tryNum) {
        return !PROJECT_NAME_PATTERN.test(project.getName()) ?
                EmptyRunnable.INSTANCE :
                () -> {
                    Set<LibraryOrderEntry> sourceless = project.isDisposed() ?
                            Collections.emptySet() :
                            application.runReadAction(getSourcelessLibraries(project));

                    if (sourceless.isEmpty()) {
                        if (tryNum < MAX_RETRIES) {
                            // onSuccess is sometimes called before unmanagedJars has finished importing
                            LOG.info("No candidates found, rescheduling check for " + DELAY);
                            new Alarm().addRequest(doAttach(application, project, tryNum + 1), DELAY);
                        }
                    } else {
                        Optional<VirtualFile> maybeLibraryRoot = application.runReadAction(getLibraryRoot(project.getBaseDir(), sourceless));

                        if (!maybeLibraryRoot.isPresent()) {
                            LOG.error("No libraries with valid class roots found");
                            return;
                        }

                        VirtualFile libraryRoot = maybeLibraryRoot.get();
                        VirtualFile sourcesZip = findSourcesZip(libraryRoot);
                        if (sourcesZip == null) {
                            LOG.error("Sources archive not found in: " + libraryRoot.getCanonicalPath());
                            return;
                        }

                        new Task.Backgroundable(project, "Attaching Idea Sources", true) {
                            @Override
                            public void run(@NotNull ProgressIndicator indicator) {
                                LOG.info("Sources archive found: " + sourcesZip.getCanonicalPath());

                                Collection<VirtualFile> roots = JavaVfsSourceRootDetectionUtil.suggestRoots(sourcesZip, indicator);

                                TransactionGuard.getInstance().submitTransactionLater(myProject, () -> {
                                    sourceless.stream()
                                            .map(LibraryOrderEntry::getLibrary)
                                            .filter(LibraryEx.class::isInstance)
                                            .map(LibraryEx.class::cast)
                                            .forEach(library -> application.runWriteAction(addLibrary(library, roots)));
                                    LOG.info("Finished attaching IDEA sources");
                                });
                            }

                            private Runnable addLibrary(LibraryEx library, Collection<VirtualFile> roots) {
                                return () -> {
                                    if (library.isDisposed()) return;

                                    Library.ModifiableModel model = library.getModifiableModel();
                                    roots.forEach(file -> model.addRoot(file, OrderRootType.SOURCES));
                                    model.commit();
                                };
                            }
                        }.queue();
                    }
                };
    }

    @NotNull
    private static Computable<Optional<VirtualFile>> getLibraryRoot(@NotNull VirtualFile projectDirectory,
                                                                    @NotNull Set<LibraryOrderEntry> sourceless) {
        return () -> sourceless
                .stream()
                .map(entry -> entry.getFiles(OrderRootType.CLASSES))
                .map(jarFiles -> findDirectory(jarFiles, projectDirectory))
                .filter(Objects::nonNull)
                .findFirst();
    }

    @NotNull
    private static Computable<Set<LibraryOrderEntry>> getSourcelessLibraries(@NotNull Project project) {
        return () -> Arrays
                .stream(PackageIndex.getInstance(project).getDirectoriesByPackageName("com.intellij", false))
                .flatMap(file -> getLibraryOrderEntries(project, file))
                .filter(IntelliJSourcesAttachListener::isSourceless)
                .collect(Collectors.toSet());
    }

    @NotNull
    private static Stream<LibraryOrderEntry> getLibraryOrderEntries(@NotNull Project project, @NotNull VirtualFile file) {
        return file.getUrl().contains(".jar!") ?
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
    private static VirtualFile findDirectory(@NotNull VirtualFile[] jarFiles, @NotNull VirtualFile baseDirectory) {
        VirtualFile jarFile = findJarFile(jarFiles);
        VirtualFile parent = VfsUtil.getVirtualFileForJar(jarFile);

        while (parent != null) {
            if (DIRECTORY_PATTERN.test(parent.getName()) || parent.equals(baseDirectory)) {
                break;
            } else {
                parent = parent.getParent();
            }
        }
        return parent;
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

    @Nullable
    private static VirtualFile findSourcesZip(@NotNull VirtualFile root) {
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

        return result[0];
    }
}
