package org.jetbrains.plugins.scala.lang.superMember;

import com.intellij.testFramework.PsiTestCase;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.ScalaLoader;

import java.io.File;
import java.io.IOException;

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.11.2008
 */
public class SuperMemberTest extends PsiTestCase {
  private static String JDK_HOME = TestUtils.getMockJdk();
  public static String rootPath = TestUtils.getTestDataPath() + "/supers/";
  private static final String CARET_MARKER = "<caret>";

  private String removeMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  public void testToString() throws Exception {
    String name = "objectMethods/toString.scala";
    runTest(name);
  }

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

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject.getComponent(SyntheticClasses.class).registerClasses();
    ScalaLoader.loadScala();

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();

    VirtualFile testDataRoot = LocalFileSystem.getInstance().findFileByPath(rootPath);
    String testName = getTestName(true) + ".scala";
    assertNotNull(testDataRoot);
    File dir = createTempDir("scalaTest");
    VirtualFile vDir = LocalFileSystem.getInstance().
        refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
    assertNotNull(vDir);
    configureFile(testDataRoot, testName, vDir);

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

  private void runTest(String name) throws Exception {
    String filePath = rootPath + name;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(filePath.
        replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);
    String text = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    final String fileName = vFile.getName();

    int offset = text.indexOf(CARET_MARKER);
    text = removeMarker(text);

    myFile = createFile(myModule, fileName, text);

    filePath = filePath.replaceFirst("[.][s][c][a][l][a]", ".test");
    final VirtualFile answerFile = LocalFileSystem.getInstance().findFileByPath(filePath.
        replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", answerFile);
    String resText = StringUtil.convertLineSeparators(VfsUtil.loadText(answerFile), "\n");
    assertEquals(SuperMethodTestUtil.transform(myFile, offset), resText);
  }
}
