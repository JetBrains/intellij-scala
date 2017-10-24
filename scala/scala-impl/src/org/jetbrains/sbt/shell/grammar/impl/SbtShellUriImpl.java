// This is a generated file. Not intended for manual editing.
package org.jetbrains.sbt.shell.grammar.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.jetbrains.sbt.shell.grammar.SbtShellTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import org.jetbrains.sbt.shell.grammar.*;

public class SbtShellUriImpl extends ASTWrapperPsiElement implements SbtShellUri {

  public SbtShellUriImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull SbtShellVisitor visitor) {
    visitor.visitUri(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof SbtShellVisitor) accept((SbtShellVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getUristring() {
    return findNotNullChildByType(URISTRING);
  }

}
