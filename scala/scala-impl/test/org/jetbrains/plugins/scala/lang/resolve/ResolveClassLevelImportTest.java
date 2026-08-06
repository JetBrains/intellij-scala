package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;

import java.nio.file.Path;

public class ResolveClassLevelImportTest extends ScalaResolveTestCase {
  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("class").resolve("classLevelImport");
  }

  public void testclassLevelImport() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTemplateDefinition);
    assertEquals("scala.collection.immutable.Map", ((ScTemplateDefinition) resolved).qualifiedName());
  }
}
