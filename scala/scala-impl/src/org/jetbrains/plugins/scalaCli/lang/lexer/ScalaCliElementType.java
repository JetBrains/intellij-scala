package org.jetbrains.plugins.scalaCli.lang.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scalaCli.ScalaCliLanguage;

public class ScalaCliElementType extends IElementType {

    public ScalaCliElementType(@NonNls @NotNull String debugName) {
        super(debugName, ScalaCliLanguage.INSTANCE);
    }
}
