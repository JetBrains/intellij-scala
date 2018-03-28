package org.jetbrains.plugins.scaladev;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.JavaVfsSourceRootDetectionUtil;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.project.notification.source.AttachSourcesUtil;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IdeaSourcesAttach extends AbstractProjectComponent {

    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^(scalaUltimate|scalaCommunity)$");
    private static final Pattern JAR_PATTERN = Pattern.compile("^sources\\.(zip|jar)$");
    private static final Pattern DIRECTORY_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    protected IdeaSourcesAttach(Project project) {
        super(project);
    }

    private final Logger LOG = Logger.getInstance(this.getClass());

    @NotNull
    @Override
    public String getComponentName() {
        return "IdeaSourcesAttach";
    }

    @Override
    public void projectOpened() {
        try {
            attachIdeaSources();
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    void attachIdeaSources() {
        if (!ApplicationManager.getApplication().isInternal()) return;
        if (!PROJECT_NAME_PATTERN.matcher(myProject.getName()).matches()) return;

        new Task.Backgroundable(myProject, "Attaching Idea Sources", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Backgroundable backgroundable = this;
                ApplicationManager.getApplication().runReadAction(() -> doAttach(backgroundable, indicator));

            }
        }.queue();
    }

    Set<LibraryOrderEntry> needsAttaching() {
        if (myProject.isDisposed()) return Collections.emptySet();

        return getIntellijJars(myProject)
                .map(IdeaSourcesAttach::asSourcelessLibraryEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void doAttach(@NotNull Task task, @NotNull ProgressIndicator indicator) {
        Set<LibraryOrderEntry> sourcesless = needsAttaching();
        LOG.info("Got " + sourcesless.size() + " total IDEA libraries with missing source roots");
        if (sourcesless.isEmpty()) return;

        VirtualFile sdk = null;
        for (LibraryOrderEntry entry : sourcesless) {
            sdk = findLibraryRoot(entry);
            if (sdk != null) break;
        }

        if (sdk == null) {
            LOG.error("No libraries with valid class roots found");
            return;
        }

        VirtualFile rootDirectory = findDirectory(sdk, myProject.getBaseDir());
        if (rootDirectory == null) return;

        final VirtualFile zip = findSourcesZip(rootDirectory);
        if (zip == null) return;

        LOG.info("Found related sources archive: " + zip.getCanonicalPath());
        task.setTitle("Scanning for Sources Archive");
        Collection<VirtualFile> roots = JavaVfsSourceRootDetectionUtil.suggestRoots(zip, indicator);

        task.setTitle("Attaching Source Roots");
        for (LibraryOrderEntry entry : sourcesless) {
            TransactionGuard.getInstance()
                    .submitTransactionLater(myProject, () -> AttachSourcesUtil.appendSources(entry.getLibrary(), roots));
        }
        LOG.info("Finished attaching IDEA sources");
    }


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
    private static VirtualFile findDirectory(@Nullable VirtualFile root, @NotNull VirtualFile baseDirectory) {
        VirtualFile parent = root;
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
