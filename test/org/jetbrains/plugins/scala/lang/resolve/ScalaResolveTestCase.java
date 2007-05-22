/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.testFramework.ResolveTestCase;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.ScalaLoader;

/**
 * @author ilyas
 */
public abstract class ScalaResolveTestCase extends ResolveTestCase {
  private static String JDK_HOME = TestUtils.getTestDataPath() + "/mockJDK";

  protected abstract String getTestDataPath();

  protected void setUp() throws Exception {
    super.setUp();
    ScalaLoader.loadScala();

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(getTestDataPath());
    assertNotNull(root);
    ContentEntry contentEntry = rootModel.addContentEntry(root);
    rootModel.setJdk(JavaSdk.getInstance().createJdk("java sdk", JDK_HOME, false));
    contentEntry.addSourceFolder(root, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
  }
}
