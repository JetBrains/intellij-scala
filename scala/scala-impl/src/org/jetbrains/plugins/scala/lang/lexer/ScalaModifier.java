package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.Nullable;

public enum ScalaModifier {
    Private(PsiModifier.PRIVATE),
    Protected(PsiModifier.PROTECTED),
    Final(PsiModifier.FINAL),
    Abstract(PsiModifier.ABSTRACT),
    Override(ScalaModifier.OVERRIDE),
    Implicit(ScalaModifier.IMPLICIT),
    Using(ScalaModifier.USING),
    Sealed(ScalaModifier.SEALED),
    Lazy(ScalaModifier.LAZY),
    Case(ScalaModifier.CASE),
    Inline(ScalaModifier.INLINE),
    Transparent(ScalaModifier.TRANSPARENT),
    Open(ScalaModifier.OPEN),
    Opaque(ScalaModifier.OPAQUE),
    Infix(ScalaModifier.INFIX);

    private final String text;

    ScalaModifier(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    @Nullable
    public static ScalaModifier byText(@Nullable String text) {
        if (text == null)
            return null;

        for (ScalaModifier modifier: ScalaModifier.values()) {
            if (modifier.text.equals(text))
                return modifier;
        }
        return null;
    }

    public static final String PRIVATE = PsiModifier.PRIVATE;
    public static final String PROTECTED = PsiModifier.PROTECTED;
    public static final String FINAL = PsiModifier.FINAL;
    public static final String ABSTRACT = PsiModifier.ABSTRACT;
    public static final String CASE = "case";
    public static final String IMPLICIT = "implicit";
    public static final String USING = "using";
    public static final String LAZY = "lazy";
    public static final String OVERRIDE = "override";
    public static final String SEALED = "sealed";
    public static final String INLINE = "inline";
    public static final String TRANSPARENT = "transparent";
    public static final String OPEN = "open";
    public static final String OPAQUE = "opaque";
    public static final String INFIX = "infix";
}
