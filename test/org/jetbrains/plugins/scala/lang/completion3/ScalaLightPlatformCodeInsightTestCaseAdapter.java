package org.jetbrains.plugins.scala.lang.completion3;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.Processor;
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
  private String JDK_HOME = TestUtils.getMockJdk();

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

  protected VirtualFile getSourceRootAdapter() {
    return getSourceRoot();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdk.getInstance().createJdk("java sdk", JDK_HOME, false);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final SyntheticClasses syntheticClasses = getProject().getComponent(SyntheticClasses.class);
    if (!syntheticClasses.isClassesRegistered()) {
      syntheticClasses.registerClasses();
    }

    ModifiableRootModel rootModel = null;
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
    

    if (rootPath() != null) {
      rootModel = rootManager.getModifiableModel();
      final VirtualFile testDataRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath());
      assert(testDataRoot != null);

      refreshDirectory(testDataRoot);

      final ContentEntry contentEntry = rootModel.addContentEntry(testDataRoot);
      contentEntry.addSourceFolder(testDataRoot, false);
      
    }

    // Add Scala Library
    OrderEnumerator libs = rootManager.orderEntries().librariesOnly();
    Library.ModifiableModel libModel = null;
    final String scalaLibraryName = "scala_lib";
    class CustomProcessor implements Processor<Library> {
      public boolean result = true;

      public boolean process(Library library) {
        boolean res = library.getName().equals(scalaLibraryName);
        if (res) result = false;
        return result;
      }
    }
    CustomProcessor processor = new CustomProcessor();
    libs.forEachLibrary(processor);
    if (processor.result) {
      if (rootModel == null) {
        rootModel = rootManager.getModifiableModel();
      }
      final LibraryTable libraryTable = rootModel.getModuleLibraryTable();
      final Library scalaLib = libraryTable.createLibrary(scalaLibraryName);
      libModel = scalaLib.getModifiableModel();
      final File libRoot = new File(TestUtils.getMockScalaLib());
      assert(libRoot.exists());

      final File srcRoot = new File(TestUtils.getMockScalaSrc());
      assert(srcRoot.exists());

      libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES);
      libModel.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES);
      
      
    }
    if (libModel != null || rootModel != null) {
      final Library.ModifiableModel finalLibModel = libModel;
      final ModifiableRootModel finalRootModel = rootModel;
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (finalLibModel != null)
            finalLibModel.commit();
          if (finalRootModel != null)
            finalRootModel.commit();
          final StartupManagerImpl startupManager = (StartupManagerImpl) StartupManager.getInstance(ourProject);
          startupManager.startCacheUpdate();
        }
      });
    }
  }

  protected VirtualFile getVFileAdapter() {
    return getVFile();
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

  protected PsiManager getPsiManagerAdapter() {
    return getPsiManager();
  }

  protected void configureFromFileTextAdapter(@NonNls final String fileName,
                                              @NonNls final String fileText) throws IOException {
    configureFromFileText(fileName, fileText);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    if (rootPath() != null) {
      new WriteAction<Object>() {
        @Override
        protected void run(Result<Object> objectResult) throws Throwable {
          closeAndDeleteProject();
        }
      }.execute().throwException();
    }
  }
}