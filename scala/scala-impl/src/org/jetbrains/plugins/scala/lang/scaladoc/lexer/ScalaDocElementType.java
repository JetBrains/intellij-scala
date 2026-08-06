package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage;

public class ScalaDocElementType extends IElementType {

    public ScalaDocElementType(@NotNull @NonNls String debugName) {
        super(debugName, ScalaDocLanguage.INSTANCE);
  }
}
