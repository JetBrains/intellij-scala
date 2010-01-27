package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @author ilyas
 */
public class ResolveClassTest extends ScalaResolveTestCase {

  public String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/class/";
  }

  public void testCaseClass() throws Exception {
    PsiReference ref = configureByFile("companion/CaseClass.scala");
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScObject);
  }

  public void testApplyToCase() throws Exception {
    PsiReference ref = configureByFile("companion/ApplyToCase.scala");
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScClass);
  }

  public void testApplyToObjectApply() throws Exception {
    PsiReference ref = configureByFile("companion/ApplyToObjectApply.scala");
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScFunction);
  }

  public void testEmptySelfReference() throws Exception {
    PsiReference ref = configureByFile("selftype/selftype1.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals(trait.getName(), "Inner");
  }

  public void testEmptySelfReference2() throws Exception {
    PsiReference ref = configureByFile("selftype/selftype2.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals(trait.getName(), "Inner");
  }

  public void testEmptySelfReference3() throws Exception {
    PsiReference ref = configureByFile("selftype/selftype3.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals(trait.getName(), "Inner");
  }

  public void testEmptySelfReference4() throws Exception {
    PsiReference ref = configureByFile("selftype/selftype4.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals(trait.getName(), "Inner");
  }

  public void testImportFromParam() throws Exception {
    PsiReference ref = configureByFile("dependent/MainXMLExporter.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScClass);
  }

  public void testImportFromVal() throws Exception {
    PsiReference ref = configureByFile("dependent2/a.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScClass);
  }

  public void testSeqClass() throws Exception {
    PsiReference ref = configureByFile("sdk1/sdk1.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAliasDefinition);
    assertEquals(((ScTypeAliasDefinition) resolved).getName(), "Seq");
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

  public void testWildcardImport4() throws Exception {
    PsiReference ref = configureByFile("wild4/A.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScReferencePattern);
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