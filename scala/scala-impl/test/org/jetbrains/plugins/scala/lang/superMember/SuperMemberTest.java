package org.jetbrains.plugins.scala.lang.superMember;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.11.2008
 */
public class SuperMemberTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private static final String CARET_MARKER = "<caret>";

  @Override
  protected String rootPath() {
    return TestUtils.getTestDataPath() + "/supers/";
  }

  private String removeMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  public void testToString() throws Exception {
    String name = "objectMethods/toString.scala";
    runTest(name);
  }

  public void testHashCode() throws Exception {
    String name = "objectMethods/hashCode.scala";
    runTest(name);
  }

  public void testTraitSuper() throws Exception {
    String name = "traits/traitSuper.scala";
    runTest(name);
  }

  public void testClassAliasSuper() throws Exception {
    String name = "class/ClassAliasDependent.scala";
    runTest(name);
  }

  public void testSelfType() throws Exception {
    String name = "selfType/SelfType.scala";
    runTest(name);
  }

  private void runTest(String name) throws Exception {
    String filePath = rootPath() + name;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(filePath.
        replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);
    String text = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    final String fileName = vFile.getName();

    int offset = text.indexOf(CARET_MARKER);
    text = removeMarker(text);

    myFile = createFile(fileName, text);

    filePath = filePath.replaceFirst("[.][s][c][a][l][a]", ".test");
    final VirtualFile answerFile = LocalFileSystem.getInstance().findFileByPath(filePath.
        replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", answerFile);
    String resText = StringUtil.convertLineSeparators(VfsUtil.loadText(answerFile), "\n");
    assertEquals(resText, SuperMethodTestUtil.transform(myFile, offset));
  }
}
