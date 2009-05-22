package org.jetbrains.plugins.scala.lang.resolve.aux;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;

/**
 * @author ilyas
 */
public class CyclicDependenciesTest extends ScalaResolveTestCase {
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/aux/";
  }

  public void testCyclicSelfType() throws Exception {

    final String filePath = "stevens/test/TestJava.java";
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/')).getParent().getParent();
    addSourceContentToRoots(myModule, vFile);

    PsiReference ref = configureByFile(filePath);
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof ScClass);

    final ScClass clazz = (ScClass) resolved;
    final PsiClass[] supers = clazz.getSupers();

    assertTrue(supers.length == 1);
    final String name = supers[0].getName();
    assertTrue(name.equals("ScalaObject"));

  }

}

