// This is a generated file. Not intended for manual editing.
package org.jetbrains.sbt.shell.grammar;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SbtShellScopedKey extends PsiElement {

  @Nullable
  SbtShellConfig getConfig();

  @Nullable
  SbtShellIntask getIntask();

  @NotNull
  SbtShellKey getKey();

  @Nullable
  SbtShellProjectId getProjectId();

  @Nullable
  SbtShellUri getUri();

}
