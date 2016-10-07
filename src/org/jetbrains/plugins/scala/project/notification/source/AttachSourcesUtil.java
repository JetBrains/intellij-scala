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
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.FileTypeBasedRootFilter;
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
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Alexander Podkhalyuzin
 */

//todo: copy/paste from AttachSourcesNotificationProvider, remove it
public class AttachSourcesUtil {
  public static class AttachJarAsSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
    private final VirtualFile myClassFile;
    private final VirtualFile mySourceFile;
    private final Project myProject;

    public AttachJarAsSourcesAction(VirtualFile classFile, VirtualFile sourceFile, Project project) {
      myClassFile = classFile;
      mySourceFile = sourceFile;
      myProject = project;
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
      FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, true, true);
      descriptor.setTitle(ProjectBundle.message("library.attach.sources.action"));
      descriptor.setDescription(ProjectBundle.message("library.attach.sources.description"));
      final Library firstLibrary = libraries.get(0).getLibrary();
      VirtualFile[] roots = firstLibrary != null ? firstLibrary.getFiles(OrderRootType.CLASSES) : VirtualFile.EMPTY_ARRAY;
      VirtualFile[] candidates = FileChooser.chooseFiles(descriptor, myProject, roots.length == 0 ? null : roots[0]);
      final VirtualFile[] files = scanAndSelectDetectedJavaSourceRoots(myParentComponent, candidates);
      if (files.length == 0) {
        return new ActionCallback.Rejected();
      }
      final Map<Library, LibraryOrderEntry> librariesToAppendSourcesTo = new HashMap<Library, LibraryOrderEntry>();
      for (LibraryOrderEntry library : libraries) {
        librariesToAppendSourcesTo.put(library.getLibrary(), library);
      }
      if (librariesToAppendSourcesTo.size() == 1) {
        appendSources(firstLibrary, files);
      } else {
        librariesToAppendSourcesTo.put(null, null);
        final Collection<LibraryOrderEntry> orderEntries = librariesToAppendSourcesTo.values();
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<LibraryOrderEntry>("<html><body>Multiple libraries contain file.<br> Choose libraries to attach sources to</body></html>",
                                                                                              orderEntries.toArray(new LibraryOrderEntry[orderEntries.size()])){
          @Override
          public ListSeparator getSeparatorAbove(LibraryOrderEntry value) {
            return value == null ? new ListSeparator() : null;
          }

          @NotNull
          @Override
          public String getTextFor(LibraryOrderEntry value) {
            if (value != null) {
              return value.getPresentableName() + " (" + value.getOwnerModule().getName() + ")";
            }
            else {
              return "All";
            }
          }

          @Override
          public PopupStep onChosen(LibraryOrderEntry libraryOrderEntry, boolean finalChoice) {
            if (libraryOrderEntry != null) {
              appendSources(libraryOrderEntry.getLibrary(), files);
            } else {
              for (Library libOrderEntry : librariesToAppendSourcesTo.keySet()) {
                if (libOrderEntry != null) {
                  appendSources(libOrderEntry, files);
                }
              }
            }
            return FINAL_CHOICE;
          }
        }).showCenteredInCurrentWindow(myProject);
      }

      return new ActionCallback.Done();
    }
  }

  public static void appendSources(final Library library, final VirtualFile[] files) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Library.ModifiableModel model = library.getModifiableModel();
        for (VirtualFile virtualFile : files) {
          model.addRoot(virtualFile, OrderRootType.SOURCES);
        }
        model.commit();
      }
    });
  }

  /**
   * This method takes a candidates for the project root, then scans the candidates and
   * if multiple candidates or non root source directories are found whithin some
   * directories, it shows a dialog that allows selecting or deselecting them.
   * @param parentComponent a parent parent or project
   * @param rootCandidates a candidates for roots
   * @return a array of source folders or empty array if non was selected or dialog was canceled.
   */
  public static VirtualFile[] scanAndSelectDetectedJavaSourceRoots(Component parentComponent, final VirtualFile[] rootCandidates) {
    final List<RootDetector> rootDetectors = new ArrayList<RootDetector>();
    rootDetectors.add(new LibraryJavaSourceRootDetector());
    rootDetectors.add(new FileTypeBasedRootFilter(OrderRootType.SOURCES, false, ScalaFileType.SCALA_FILE_TYPE, "source"));
    final List<OrderRoot> orderRoots = RootDetectionUtil.detectRoots(Arrays.asList(rootCandidates), parentComponent, null,
        new LibraryRootsDetectorImpl(rootDetectors),
        new OrderRootType[0]);
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (OrderRoot root : orderRoots) {
      result.add(root.getFile());
    }
    return VfsUtil.toVirtualFileArray(result);
  }
}
