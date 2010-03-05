/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ResolveTestCase;
import org.jetbrains.plugins.scala.ScalaLoader;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author ilyas
 */
public abstract class ScalaResolveTestCase extends ResolveTestCase {
  private static String JDK_HOME = TestUtils.getMockJdk();

  public abstract String getTestDataPath();

  private void configureFile(final VirtualFile vFile, String exceptName, final VirtualFile newDir) {
    if (vFile.isDirectory()) {
      for (VirtualFile file : vFile.getChildren()) {
        configureFile(file, exceptName, newDir);
      }
    } else {
      if (vFile.getName().equals(exceptName)) {
        return;
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            vFile.copy(null, newDir, vFile.getName());
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      });
    }
  }

  public boolean allSourcesFromDirectory() {
    return false;
  }

  protected void setUp() throws Exception {
    super.setUp();
    myProject.getComponent(SyntheticClasses.class).registerClasses();
    ScalaLoader.loadScala();

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();

    VirtualFile testDataRoot = LocalFileSystem.getInstance().findFileByPath(getTestDataPath());
    String testName = getTestName(true) + ".scala";
    assertNotNull(testDataRoot);
    File dir = createTempDir("scalaTest");
    VirtualFile vDir = LocalFileSystem.getInstance().
        refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
    assertNotNull(vDir);
    if (allSourcesFromDirectory()) configureFile(testDataRoot, testName, vDir);

    ContentEntry contentEntry = rootModel.addContentEntry(vDir);
    rootModel.setSdk(JavaSdk.getInstance().createJdk("java sdk", JDK_HOME, false));
    contentEntry.addSourceFolder(vDir, false);

    // Add Scala Library
    LibraryTable libraryTable = rootModel.getModuleLibraryTable();
    Library scalaLib = libraryTable.createLibrary("scala_lib");
    final Library.ModifiableModel libModel = scalaLib.getModifiableModel();
    File libRoot = new File(TestUtils.getMockScalaLib());
    assertTrue(libRoot.exists());

    File srcRoot = new File(TestUtils.getMockScalaSrc());
    assertTrue(srcRoot.exists());

    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES);
    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        libModel.commit();
        rootModel.commit();
      }
    });
  }
}
