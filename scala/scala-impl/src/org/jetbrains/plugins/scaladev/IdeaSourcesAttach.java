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
import com.intellij.openapi.roots.ui.configuration.LibraryJavaSourceRootDetector;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.project.notification.source.AttachSourcesUtil;

import java.util.*;
import java.util.regex.Pattern;

public class IdeaSourcesAttach extends AbstractProjectComponent {
    private static final Pattern PATTERN = Pattern.compile("^sources\\.(zip|jar)$");
    private static final HashSet PRJ_NAMES = new HashSet<>(Arrays.asList("scalaUltimate", "scalaCommunity"));

    protected IdeaSourcesAttach(Project project) {
        super(project);
    }

    private final Logger LOG = Logger.getInstance(this.getClass());

    public static final String NAME = "IdeaSourcesAttach";

    @NotNull
    @Override
    public String getComponentName() {
        return NAME;
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
        if (!PRJ_NAMES.contains(myProject.getName())) return;

        new Task.Backgroundable(myProject, "Attaching Idea Sources", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                final Set<LibraryOrderEntry> libs = getLibsWithoutSourceRoots();
                LOG.info("Got " + libs.size() + " total IDEA libraries with missing source roots");
                if (libs.isEmpty()) return;
                LibraryOrderEntry pivot = null;
                for (LibraryOrderEntry lib : libs) {
                    Library library = lib.getLibrary();
                    if (library != null && library.getFiles(OrderRootType.CLASSES).length > 0) {
                        pivot = lib;
                        break;
                    }
                }
                if (pivot == null) {
                    LOG.error("No libraries with valid class roots found");
                    return;
                }

                final VirtualFile zip = findSourcesZip(findCurrentSDKDir(pivot));
                if (zip == null) return;
                LOG.info("Found related sources archive: " + zip.getCanonicalPath());
                setTitle("Scanning for Sources Archive");
                final Collection<VirtualFile> roots = new LibraryJavaSourceRootDetector().detectRoots(zip, indicator);
                setTitle("Attaching Source Roots");
                for (LibraryOrderEntry lib : libs) {
                    final Library library = lib.getLibrary();
                    if (library != null && library.getUrls(OrderRootType.SOURCES).length == 0) {
                        TransactionGuard.getInstance().submitTransactionLater(myProject, () ->
                                AttachSourcesUtil.appendSources(library, roots.toArray(new VirtualFile[roots.size()]))
                        );
                    }
                }
                LOG.info("Finished attaching IDEA sources");
            }
        }.queue();
    }

    Set<LibraryOrderEntry> getLibsWithoutSourceRoots() {
        return needsAttaching(getIntellijJars());
    }

    private HashSet<LibraryOrderEntry> getIntellijJars() {
        if (myProject.isDisposed()) return new HashSet<LibraryOrderEntry>(0);
        VirtualFile[] all = PackageIndex.getInstance(myProject).getDirectoriesByPackageName("com.intellij", false);
        ArrayList<VirtualFile> jars = new ArrayList<VirtualFile>();
        for (VirtualFile f : all) {
            if (f.getUrl().contains(".jar!")) jars.add(f);
        }
        ArrayList<LibraryOrderEntry> libs = new ArrayList<LibraryOrderEntry>();
        for (VirtualFile jar : jars) {
            for (OrderEntry orderEntry : ProjectFileIndex.SERVICE.getInstance(myProject).getOrderEntriesForFile(jar)) {
                if (orderEntry instanceof LibraryOrderEntry) libs.add((LibraryOrderEntry) orderEntry);
            }
        }
        return new HashSet<>(libs);
    }

    private Set<LibraryOrderEntry> needsAttaching(final Set<LibraryOrderEntry> libs) {
        final Set<LibraryOrderEntry> res = new HashSet<LibraryOrderEntry>();
        for (LibraryOrderEntry lib : libs) {
            Library library = lib.getLibrary();
            if ((library != null) && (library.getUrls(OrderRootType.SOURCES).length == 0))
                res.add(lib);
        }
        return res;
    }

    private VirtualFile findSourcesZip(VirtualFile root) {
        final VirtualFile[] res = {null};
        try {
            VfsUtilCore.visitChildrenRecursively(root,
                    new VirtualFileVisitor() {
                        @NotNull
                        @Override
                        public Result visitFileEx(@NotNull VirtualFile file) {
                            if (PATTERN.matcher(file.getName()).matches()) {
                                res[0] = VirtualFileManager.getInstance().findFileByUrl("jar://" + file.getCanonicalPath() + "!/");
                                throw new RuntimeException();
                            }
                            return VirtualFileVisitor.CONTINUE;
                        }
                    });
        } catch (Exception ignored) {

        }
        return res[0];
    }

    private VirtualFile findCurrentSDKDir(LibraryOrderEntry anyLib) {
        String path = anyLib.getFiles(OrderRootType.CLASSES)[0].getCanonicalPath();
        if (path == null) return null;
        VirtualFile parent = VirtualFileManager.getInstance().findFileByUrl("file://" + path.substring(0, path.length() - 2));
        while (parent != null) {
            if (parent.getName().matches("^\\d+\\.\\d+\\.\\d+$") || parent.equals(myProject.getBaseDir()))
                return parent;
            parent = parent.getParent();
        }
        return null;
    }
}
