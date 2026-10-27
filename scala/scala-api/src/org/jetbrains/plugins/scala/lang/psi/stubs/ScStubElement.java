package org.jetbrains.plugins.scala.lang.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Redeclares the Java default method as abstract so Scala stub traits use the final
 * implementation inherited from {@link com.intellij.psi.stubs.StubBase}.
 * This declaration must be in Java: an abstract Scala override does not suppress
 * an inherited concrete default method. It lives in scala-api so javac compiles
 * it before scalac reads it; mixed Java/Scala compilation does not preserve this
 * distinction either. Scala subtraits must also inherit this interface directly
 * to retain the abstract declaration when resolving inherited members.
 */
public interface ScStubElement<T extends PsiElement> extends StubElement<T> {
    @Override
    @Nullable
    StubElement<? extends PsiElement> findChildStubByElementType(@NotNull IElementType elementType);
}
