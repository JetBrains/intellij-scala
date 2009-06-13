package org.jetbrains.plugins.scala.lang.resolve.aux1;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiNamedElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;

/**
 * @author ilyas
 */
public class DependenciesResolveTest extends ScalaResolveTestCase {
  public String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/aux1/";
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

  public void testDependentClass() throws Exception {
    final String filePath = "idea/LocalImport.scala";
    PsiReference ref = configureByFile(filePath);
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof ScClass);

    final ScClass clazz = (ScClass) resolved;
    final String name = clazz.getName();
    assertTrue(name.equals("Description"));

  }

  public void testDependentType() throws Exception {
    final String filePath = "idea/LocalImport1.scala";
    PsiReference ref = configureByFile(filePath);
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof ScTypeAlias);

    final ScTypeAlias alias = (ScTypeAlias) resolved;
    final String name = alias.getName();
    assertTrue(name.equals("Den"));

  }

  public void testDependentValue() throws Exception {
    final String filePath = "idea/LocalImport2.scala";
    PsiReference ref = configureByFile(filePath);
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof PsiNamedElement);

    final PsiNamedElement named = (PsiNamedElement) resolved;
    final String name = named.getName();
    assertTrue("popa".equals(name));

  }

}

