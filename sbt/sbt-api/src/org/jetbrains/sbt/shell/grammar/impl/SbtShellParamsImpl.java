// This is a generated file. Not intended for manual editing.
package org.jetbrains.sbt.shell.grammar.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.sbt.shell.grammar.SbtShellParams;
import org.jetbrains.sbt.shell.grammar.SbtShellVisitor;

public class SbtShellParamsImpl extends ASTWrapperPsiElement implements SbtShellParams {

  public SbtShellParamsImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull SbtShellVisitor visitor) {
    visitor.visitParams(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof SbtShellVisitor) accept((SbtShellVisitor)visitor);
    else super.accept(visitor);
  }

}
