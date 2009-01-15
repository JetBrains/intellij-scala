package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @author ilyas
 */
public class ResolveClassTest extends ScalaResolveTestCase {

  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/class/";
  }

  public void testSeqClass() throws Exception {
    PsiReference ref = configureByFile("sdk1/sdk1.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiClass);
    assertEquals(((PsiClass) resolved).getQualifiedName(), "scala.Seq");
  }

  public void testLocalClass() throws Exception {
    PsiReference ref = configureByFile("loc/MyClass.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    assertEquals(((PsiClass) resolved).getQualifiedName(), "org.MyTrait");
  }

  public void testWildcardImport() throws Exception {
    PsiReference ref = configureByFile("wild/A.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScClass);
    assertEquals(((PsiClass) resolved).getQualifiedName(), "bbb.BB");
  }

  public void testWildcardImport2() throws Exception {
    PsiReference ref = configureByFile("wild2/A.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScClass);
    assertEquals(((PsiClass) resolved).getQualifiedName(), "Foo.BB");
  }

  public void testWildcardImport3() throws Exception {
    PsiReference ref = configureByFile("wild3/A.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScClass);
    assertEquals(((PsiClass) resolved).getQualifiedName(), "AAA.CaseClass");
  }

  public void testLocalClass2() throws Exception {
    PsiReference ref = configureByFile("loc2/loc2.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    assertEquals(((PsiClass) resolved).getQualifiedName(), "MyTrait");
  }

  public void testClassLevelImport() throws Exception {
    PsiReference ref = configureByFile("classLevelImport/classLevelImport.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiClass);
    assertEquals(((PsiClass) resolved).getQualifiedName(), "scala.collection.immutable.Map");
  }
}