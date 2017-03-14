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

public class SbtShellScopedKeyImpl extends ASTWrapperPsiElement implements SbtShellScopedKey {

  public SbtShellScopedKeyImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull SbtShellVisitor visitor) {
    visitor.visitScopedKey(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof SbtShellVisitor) accept((SbtShellVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public SbtShellConfig getConfig() {
    return findChildByClass(SbtShellConfig.class);
  }

  @Override
  @Nullable
  public SbtShellIntask getIntask() {
    return findChildByClass(SbtShellIntask.class);
  }

  @Override
  @NotNull
  public SbtShellKey getKey() {
    return findNotNullChildByClass(SbtShellKey.class);
  }

  @Override
  @Nullable
  public SbtShellProjectId getProjectId() {
    return findChildByClass(SbtShellProjectId.class);
  }

  @Override
  @Nullable
  public SbtShellUri getUri() {
    return findChildByClass(SbtShellUri.class);
  }

}
