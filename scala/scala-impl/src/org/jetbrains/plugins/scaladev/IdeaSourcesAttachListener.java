package org.jetbrains.plugins.scaladev;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.JavaVfsSourceRootDetectionUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.*;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.project.notification.source.AttachSourcesUtil;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IdeaSourcesAttachListener extends ExternalSystemTaskNotificationListenerAdapter {

    private static final Logger LOG = Logger.getInstance(IdeaSourcesAttachListener.class);

    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^scalaUltimate|scalaCommunity|((scala-plugin-for-ultimate|intellij-scala)(_\\d+)?)$");
    private static final Pattern JAR_PATTERN = Pattern.compile("^sources\\.(zip|jar)$");
    private static final Pattern DIRECTORY_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private final static int MAX_RETRIES = 3;
    private final static int DELAY = 10000;

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        Project project = id.findProject();
        if (project == null) return;

        Application application = ApplicationManager.getApplication();
        if (!application.isInternal()) return;

        Runnable activity = doAttach(application, project, 0);
        application.invokeLater(activity);
    }

    @NotNull
    private static Runnable doAttach(@NotNull Application application,
                                     @NotNull Project project,
                                     int tryNum) {
        Set<LibraryOrderEntry> sourcesless = librariesLackSources(application, project);

        if (sourcesless.isEmpty()) return () -> {
            if (tryNum < MAX_RETRIES) {
                // onSuccess is sometimes called before unmanagedJars has finished importing
                LOG.info("No candidates found, rescheduling check for " + DELAY);
                new Alarm().addRequest(doAttach(application, project, tryNum + 1), DELAY);
            }
        };

        return () -> {
            if (!PROJECT_NAME_PATTERN.matcher(project.getName()).matches()) return;

            VirtualFile libraryRoot = application.runReadAction((Computable<VirtualFile>) () -> {
                for (LibraryOrderEntry entry : sourcesless) {
                    VirtualFile root = findDirectory(entry, project.getBaseDir());
                    if (root != null) return root;
                }
                return null;
            });

            if (libraryRoot == null) {
                LOG.error("No libraries with valid class roots found");
                return;
            }

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
                        for (LibraryOrderEntry entry : sourcesless) {
                            Library library = entry.getLibrary();
                            if (library == null) continue;

                            AttachSourcesUtil.appendSources(library, roots);
                        }
                    });

                    LOG.info("Finished attaching IDEA sources");

                }
            }.queue();
        };
    }

    @NotNull
    private static Set<LibraryOrderEntry> librariesLackSources(@NotNull Application application,
                                                               @NotNull Project project) {
        if (project.isDisposed()) return Collections.emptySet();

        return application.runReadAction((Computable<Set<LibraryOrderEntry>>) () ->
                getIntellijJars(project)
                        .map(IdeaSourcesAttachListener::asSourcelessLibraryEntry)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );
    }

    @NotNull
    private static Stream<OrderEntry> getIntellijJars(@NotNull Project project) {
        PackageIndex packageIndex = PackageIndex.getInstance(project);
        ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);

        VirtualFile[] intellijDirectories = packageIndex.getDirectoriesByPackageName("com.intellij", false);
        return Arrays.stream(intellijDirectories)
                .filter(file -> file.getUrl().contains(".jar!"))
                .flatMap(jarFile -> fileIndex.getOrderEntriesForFile(jarFile).stream());
    }

    @Nullable
    private static LibraryOrderEntry asSourcelessLibraryEntry(@NotNull OrderEntry entry) {
        if (!(entry instanceof LibraryOrderEntry)) return null;

        LibraryOrderEntry result = (LibraryOrderEntry) entry;

        Library library = result.getLibrary();
        return library != null && library.getUrls(OrderRootType.SOURCES).length == 0
                ? result
                : null;
    }

    @Nullable
    private static VirtualFile findDirectory(@NotNull LibraryOrderEntry entry, @NotNull VirtualFile baseDirectory) {
        VirtualFile parent = findLibraryRoot(entry);
        while (parent != null) {
            if (DIRECTORY_PATTERN.matcher(parent.getName()).matches() || parent.equals(baseDirectory)) {
                break;
            } else {
                parent = parent.getParent();
            }
        }
        return parent;
    }

    @Nullable
    private static VirtualFile findLibraryRoot(@NotNull LibraryOrderEntry entry) {
        VirtualFile[] jars = entry.getFiles(OrderRootType.CLASSES);

        VirtualFile firstFile = at(jars, 0);
        VirtualFile secondFile = isToolsJar(firstFile)
                ? at(jars, 1)
                : firstFile;

        return VfsUtil.getVirtualFileForJar(secondFile);
    }

    @Nullable
    private static VirtualFile at(@NotNull VirtualFile[] files, int index) {
        return files.length > index ? files[index] : null;
    }

    private static boolean isToolsJar(@Nullable VirtualFile jarFile) {
        if (jarFile == null) return false;

        String path = jarFile.getCanonicalPath();
        return path != null && path.contains("tools.jar");
    }

    @Nullable
    private static VirtualFile findSourcesZip(@NotNull VirtualFile root) {
        VirtualFile[] result = {null};
        try {
            VirtualFileManager fileManager = VirtualFileManager.getInstance();
            VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
                @NotNull
                @Override
                public Result visitFileEx(@NotNull VirtualFile file) {
                    if (JAR_PATTERN.matcher(file.getName()).matches()) {
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
