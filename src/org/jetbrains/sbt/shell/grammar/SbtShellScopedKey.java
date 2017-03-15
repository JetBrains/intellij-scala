// This is a generated file. Not intended for manual editing.
package org.jetbrains.sbt.shell.grammar;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

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
