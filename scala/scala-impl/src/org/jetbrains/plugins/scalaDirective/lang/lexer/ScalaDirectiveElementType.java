package org.jetbrains.plugins.scalaDirective.lang.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveLanguage;

public class ScalaDirectiveElementType extends IElementType {

    public ScalaDirectiveElementType(@NonNls @NotNull String debugName) {
        super(debugName, ScalaDirectiveLanguage.INSTANCE);
    }
}
