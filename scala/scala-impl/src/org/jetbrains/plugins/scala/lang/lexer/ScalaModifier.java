package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.psi.PsiModifier;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public enum ScalaModifier {
    Private(PsiModifier.PRIVATE),
    Protected(PsiModifier.PROTECTED),
    Final(PsiModifier.FINAL),
    Abstract(PsiModifier.ABSTRACT),
    Override(ScalaModifier.OVERRIDE),
    Implicit(ScalaModifier.IMPLICIT),
    Sealed(ScalaModifier.SEALED),
    Lazy(ScalaModifier.LAZY),
    Case(ScalaModifier.CASE);

    private final String text;

    ScalaModifier(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    public IElementType getTokenType() {
        return tokenTypes.get(this);
    }

    @Nullable
    public static ScalaModifier byText(String text) {
        for (ScalaModifier modifier: ScalaModifier.values()) {
            if (modifier.text.equals(text))
                return modifier;
        }
        return null;
    }

    private static EnumMap<ScalaModifier, ScalaModifierTokenType> tokenTypes =
            new EnumMap<>(ScalaModifier.class);

    static void registerModifierTokenType(ScalaModifier modifier, ScalaModifierTokenType tokenType) {
        if (tokenTypes.containsKey(modifier)) {
            throw new AssertionError("Duplicated scala modifier token type");
        }

        tokenTypes.put(modifier, tokenType);
    }

    public static final String CASE = "case";
    public static final String IMPLICIT = "implicit";
    public static final String LAZY = "lazy";
    public static final String OVERRIDE = "override";
    public static final String SEALED = "sealed";
}
