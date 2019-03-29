package org.jetbrains.plugins.scala.project.notification.source;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.DescendentBasedRootFilter;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.RootDetector;
import com.intellij.openapi.roots.libraries.ui.impl.LibraryRootsDetectorImpl;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.LibraryJavaSourceRootDetector;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Alexander Podkhalyuzin
 */

//todo: copy/paste from AttachSourcesNotificationProvider, remove it
public class AttachSourcesUtil {

    public static class AttachJarAsSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
        private final VirtualFile myClassFile;

        public AttachJarAsSourcesAction(VirtualFile classFile) {
            myClassFile = classFile;
        }

        public String getName() {
            return ProjectBundle.message("module.libraries.attach.sources.immediately.button");
        }

        public String getBusyText() {
            return ProjectBundle.message("library.attach.sources.action.busy.text");
        }

        public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
            final List<Library.ModifiableModel> modelsToCommit = new ArrayList<Library.ModifiableModel>();
            for (LibraryOrderEntry orderEntry : orderEntriesContainingFile) {
                final Library library = orderEntry.getLibrary();
                if (library == null) continue;
                final VirtualFile root = findRoot(library);
                if (root == null) continue;
                final Library.ModifiableModel model = library.getModifiableModel();
                model.addRoot(root, OrderRootType.SOURCES);
                modelsToCommit.add(model);
            }
            if (modelsToCommit.isEmpty()) return new ActionCallback.Rejected();
            new WriteAction() {
                protected void run(final Result result) {
                    for (Library.ModifiableModel model : modelsToCommit) {
                        model.commit();
                    }
                }
            }.execute();

            return new ActionCallback.Done();
        }

        @Nullable
        private VirtualFile findRoot(Library library) {
            for (VirtualFile classesRoot : library.getFiles(OrderRootType.CLASSES)) {
                if (VfsUtil.isAncestor(classesRoot, myClassFile, true)) {
                    return classesRoot;
                }
            }
            return null;
        }
    }

    public static class ChooseAndAttachSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
        private final Project myProject;
        private final JComponent myParentComponent;

        public ChooseAndAttachSourcesAction(Project project, JComponent parentComponent) {
            myProject = project;
            myParentComponent = parentComponent;
        }

        public String getName() {
            return ProjectBundle.message("module.libraries.attach.sources.button");
        }

        public String getBusyText() {
            return ProjectBundle.message("library.attach.sources.action.busy.text");
        }

        public ActionCallback perform(final List<LibraryOrderEntry> libraries) {
            final Library firstLibrary = libraries.get(0).getLibrary();
            VirtualFile root = firstLibrary != null ? findRoot(firstLibrary) : null;

            List<VirtualFile> files = scanAndSelectDetectedJavaSourceRoots(root);
            if (files.isEmpty()) return new ActionCallback.Rejected();

            Map<Library, LibraryOrderEntry> librariesToAppendSourcesTo = new HashMap<>();
            for (LibraryOrderEntry library : libraries) {
                librariesToAppendSourcesTo.put(library.getLibrary(), library);
            }
            if (librariesToAppendSourcesTo.size() == 1) {
                appendSources(firstLibrary, files);
            } else {
                librariesToAppendSourcesTo.put(null, null);

                LibraryOrderEntry[] orderEntries = librariesToAppendSourcesTo.values().toArray(new LibraryOrderEntry[0]);
                BaseListPopupStep<LibraryOrderEntry> popupStep = new BaseListPopupStep<LibraryOrderEntry>("<html><body>Multiple libraries contain file.<br> Choose libraries to attach sources to</body></html>", orderEntries) {

                    @Nullable
                    @Override
                    public ListSeparator getSeparatorAbove(@Nullable LibraryOrderEntry entry) {
                        return entry == null ? new ListSeparator() : null;
                    }

                    @NotNull
                    @Override
                    public String getTextFor(@Nullable LibraryOrderEntry entry) {
                        return entry != null
                                ? entry.getPresentableName() + " (" + entry.getOwnerModule().getName() + ")"
                                : "All";
                    }

                    @Nullable
                    @Override
                    public PopupStep onChosen(@Nullable LibraryOrderEntry entry, boolean finalChoice) {
                        if (entry != null) {
                            appendSources(entry.getLibrary(), files);
                        } else {
                            for (Library library : librariesToAppendSourcesTo.keySet()) {
                                if (library != null) {
                                    appendSources(library, files);
                                }
                            }
                        }
                        return FINAL_CHOICE;
                    }
                };
                JBPopupFactory.getInstance().createListPopup(popupStep).showCenteredInCurrentWindow(myProject);
            }

            return new ActionCallback.Done();
        }

        /**
         * This method takes a candidates for the project root, then scans the candidates and
         * if multiple candidates or non root source directories are found whithin some
         * directories, it shows a dialog that allows selecting or deselecting them.
         *
         * @param root a candidates for root
         * @return a array of source folders or empty array if non was selected or dialog was canceled.
         */
        private List<VirtualFile> scanAndSelectDetectedJavaSourceRoots(@Nullable VirtualFile root) {
            List<RootDetector> rootDetectors = Arrays.asList(
                    new LibraryJavaSourceRootDetector(),
                    DescendentBasedRootFilter.createFileTypeBasedFilter(OrderRootType.SOURCES, false, ScalaFileType.INSTANCE, "source")
            );

            List<OrderRoot> orderRoots = RootDetectionUtil.detectRoots(
                    chooseFiles(myProject, root),
                    myParentComponent,
                    null,
                    new LibraryRootsDetectorImpl(rootDetectors),
                    new OrderRootType[0]
            );

            List<VirtualFile> result = orderRoots.stream()
                    .map(OrderRoot::getFile)
                    .collect(Collectors.toList());

            return Collections.unmodifiableList(result);
        }

        @Nullable
        private static VirtualFile findRoot(@NotNull Library library) {
            VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
            return files.length != 0 ? files[0] : null;
        }

        @NotNull
        private static List<VirtualFile> chooseFiles(@NotNull Project project, @Nullable VirtualFile root) {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, true, true);
            descriptor.setTitle(ProjectBundle.message("library.attach.sources.action"));
            descriptor.setDescription(ProjectBundle.message("library.attach.sources.description"));

            return Arrays.asList(FileChooser.chooseFiles(descriptor, project, root));
        }
    }

    private static void appendSources(Library library, Collection<VirtualFile> files) {
        ApplicationManager.getApplication().runWriteAction(() -> {
            if (!((LibraryEx)library).isDisposed()) {
                Library.ModifiableModel model = library.getModifiableModel();
                for (VirtualFile virtualFile : files) {
                    model.addRoot(virtualFile, OrderRootType.SOURCES);
                }
                model.commit();
            }
        });
    }
}
