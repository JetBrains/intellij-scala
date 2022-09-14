package org.jetbrains.plugins.scala.compiler.references.indices;

import com.intellij.compiler.backwardRefs.JavaCompilerRefAdapter;
import com.intellij.compiler.backwardRefs.SearchId;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import org.jetbrains.annotations.NotNull;

abstract class JavaCompilerRefAdapterCompat extends JavaCompilerRefAdapter {
  protected abstract PsiClass[] directInheritorCandidatesInFile(@NotNull SearchId[] internalNames, @NotNull PsiFileWithStubSupport file);

  protected abstract PsiFunctionalExpression[] funExpressionsInFile(@NotNull SearchId[] funExpressions, @NotNull PsiFileWithStubSupport file);

  @NotNull
  @Override
  public PsiClass[] findDirectInheritorCandidatesInFile(@NotNull SearchId[] internalNames, @NotNull PsiFileWithStubSupport file) {
    return directInheritorCandidatesInFile(internalNames, file);
  }

  @NotNull
  @Override
  public PsiFunctionalExpression[] findFunExpressionsInFile(@NotNull SearchId[] funExpressions, @NotNull PsiFileWithStubSupport file) {
    return funExpressionsInFile(funExpressions, file);
  }
}
