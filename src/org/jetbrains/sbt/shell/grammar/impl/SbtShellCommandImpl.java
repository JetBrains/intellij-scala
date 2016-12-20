// This is a generated file. Not intended for manual editing.
package org.jetbrains.sbt.shell.grammar.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.sbt.shell.grammar.SbtShellCommand;
import org.jetbrains.sbt.shell.grammar.SbtShellVisitor;
import org.jetbrains.annotations.NotNull;

public class SbtShellCommandImpl extends ASTWrapperPsiElement implements SbtShellCommand {

  public SbtShellCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull SbtShellVisitor visitor) {
    visitor.visitCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof SbtShellVisitor) accept((SbtShellVisitor)visitor);
    else super.accept(visitor);
  }

}
