package org.jetbrains.plugins.scala.lang.completion3;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticPackageHelper;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author Alexander Podkhalyuzin
 */
public abstract class ScalaLightPlatformCodeInsightTestCaseAdapter extends LightPlatformCodeInsightTestCase {
  protected String rootPath() {
    return null;
  }

  protected final String baseRootPath() {
    return TestUtils.getTestDataPath() + "/";
  }

  private void refreshDirectory(VirtualFile dir) {
    if (!dir.isDirectory()) return;
    dir.getChildren();
    LocalFileSystem.getInstance().refresh(false);
    for (VirtualFile child : dir.getChildren()) {
      refreshDirectory(child);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final SyntheticClasses syntheticClasses = getProject().getComponent(SyntheticClasses.class);
    if (!syntheticClasses.isClassesRegistered()) {
      syntheticClasses.registerClasses();
    }

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();

    if (rootPath() != null) {
      final VirtualFile testDataRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath());
      assert(testDataRoot != null);

      refreshDirectory(testDataRoot);

      final ContentEntry contentEntry = rootModel.addContentEntry(testDataRoot);
      contentEntry.addSourceFolder(testDataRoot, false);
    }

    // Add Scala Library
    final LibraryTable libraryTable = rootModel.getModuleLibraryTable();
    final Library scalaLib = libraryTable.createLibrary("scala_lib");
    final Library.ModifiableModel libModel = scalaLib.getModifiableModel();
    final File libRoot = new File(TestUtils.getMockScalaLib());
    assert(libRoot.exists());

    final File srcRoot = new File(TestUtils.getMockScalaSrc());
    assert(srcRoot.exists());

    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES);
    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        libModel.commit();
        rootModel.commit();
        final StartupManagerImpl startupManager =
            (StartupManagerImpl) StartupManager.getInstance(ourProject);
        startupManager.startCacheUpdate();
      }
    });
  }

  protected Editor getEditorAdapter() {
    return getEditor();
  }

  protected Project getProjectAdapter() {
    return getProject();
  }

  protected Module getModuleAdapter() {
    return getModule();
  }

  protected PsiFile getFileAdapter() {
    return getFile();
  }

  protected void configureFromFileTextAdapter(@NonNls final String fileName,
                                              @NonNls final String fileText) throws IOException {
    configureFromFileText(fileName, fileText);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    if (rootPath() != null) {
      closeAndDeleteProject();
    }
  }
}