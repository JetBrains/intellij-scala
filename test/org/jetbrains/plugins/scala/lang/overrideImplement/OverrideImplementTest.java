package org.jetbrains.plugins.scala.lang.overrideImplement;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import junit.framework.Test;
import org.jetbrains.plugins.scala.ScalaLoader;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaIndentOptionsProvider;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.09.2008
 */
public class OverrideImplementTest extends PsiTestCase {
  private static String JDK_HOME = TestUtils.getMockJdk();
  public static String rootPath = TestUtils.getTestDataPath() + "/override/";
  private static final String CARET_MARKER = "<caret>";
  private static final String END_MARKER = "<end>";

  private String removeMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  @Override
  protected void checkForSettingsDamage() throws Exception {

  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final SyntheticClasses syntheticClasses = myProject.getComponent(SyntheticClasses.class);
    if (!syntheticClasses.isClassesRegistered()) {
      syntheticClasses.registerClasses();
    }
    ScalaLoader.loadScala();

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    VirtualFile sdkRoot = LocalFileSystem.getInstance().findFileByPath(rootPath);
    assertNotNull(sdkRoot);
    ContentEntry contentEntry = rootModel.addContentEntry(sdkRoot);
    rootModel.setSdk(JavaSdk.getInstance().createJdk("java sdk", JDK_HOME, false));
    contentEntry.addSourceFolder(sdkRoot, false);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
  }

  /*public void testOverridedTypeAlias() throws Exception {
    String name = "genericTestCases/overridedTypeAlias.scala";
    runTest(name);
  }*/

  public void testFoo() throws Exception {
    String name = "simpleTests/foo.scala";
    runTest(name);
  }

  public void testEmptyLinePos() throws Exception {
    String name = "simpleTests/emptyLinePos.scala";
    runTest(name);
  }

  public void testNewLineBetweenMethods() throws Exception {
    String name = "simpleTests/newLineBetweenMethods.scala";
    runTest(name);
  }

  public void testNewLineUpper() throws Exception {
    String name = "simpleTests/newLineUpper.scala";
    runTest(name);
  }

  public void testOverrideFunction() throws Exception {
    String name = "simpleTests/overrideFunction.scala";
    runTest(name);
  }

  public void testOverrideTypeAlias() throws Exception {
    String name = "simpleTests/overrideTypeAlias.scala";
    runTest(name);
  }

  public void testOverrideValue() throws Exception {
    String name = "simpleTests/overrideValue.scala";
    runTest(name);
  }

  public void testOverrideVar() throws Exception {
    String name = "simpleTests/overrideVar.scala";
    runTest(name);
  }

  public void testTypeAlias() throws Exception {
    String name = "simpleTests/typeAlias.scala";
    runTest(name);
  }

  public void testVal() throws Exception {
    String name = "simpleTests/val.scala";
    runTest(name);
  }

  public void testVar() throws Exception {
    String name = "simpleTests/var.scala";
    runTest(name);
  }

  public void testList() throws Exception {
    String name = "javaTestCases/list.scala";
    runTest(name);
  }

  public void testClassTypeParam() throws Exception {
    String name = "genericTestCases/classTypeParam.scala";
    runTest(name);
  }

  public void testHardSubstituting() throws Exception {
    String name = "genericTestCases/hardSubstituting.scala";
    runTest(name);
  }

  public void testSimpleTypeParam() throws Exception {
    String name = "genericTestCases/simpleTypeParam.scala";
    runTest(name);
  }

  public void testSCL1997() throws Exception {
    String name = "bug/SCL1997.scala";
    runTest(name);
  }

  public void testSCL1999() throws Exception {
    String name = "bug/SCL1999.scala";
    runTest(name);
  }

  public void testSCL2540() throws Exception {
    String name = "bug/SCL2540.scala";
    runTest(name);
  }

  public void testSCL2010() throws Exception {
    String name = "bug/SCL2010.scala";
    runTest(name);
  }

  public void testSCL2052A() throws Exception {
    String name = "bug/SCL2052A.scala";
    runTest(name);
  }

  public void testSCL2052B() throws Exception {
    String name = "bug/SCL2052B.scala";
    runTest(name);
  }

  public void testSCL2052C() throws Exception {
    String name = "bug/SCL2052C.scala";
    runTest(name);
  }

  public void testSCL3305() throws Exception {
    String name = "bug/SCL3305.scala";
    runTest(name);
  }

  public void testUnitReturn() throws Exception {
    String name = "simpleTests/unitoverride.scala";
    runTest(name);
  }


  private void runTest(String name) throws Exception {
    String filePath = rootPath + name;
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.
        replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);
    String text = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    final String fileName = vFile.getName();

    int i = text.indexOf("\n");
    String info = text.substring(0, i);
    boolean isImplement = info.split(" ")[0].equals("implement");
    String methodName = info.split(" ")[1];
    String fileText = text.substring(i + 1);
    int end = fileText.indexOf(END_MARKER);
    assertTrue(end >= 0);
    String newFileText = fileText.substring(0, end);
    String expected = fileText.substring(end + END_MARKER.length() + 1);
    fileText = newFileText;
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);

    myFile = createFile(myModule, fileName, fileText);

    String actual = OverrideImplementTestHelper.transform(myProject, myFile, offset, isImplement, methodName);
    assertEquals(expected, actual);
  }
}
