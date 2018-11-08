package org.jetbrains.plugins.scala.codeInsight.implicits;

import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

// A workaround for the raw Inlay Java types.
// Given that a renderer is created in the context of Editor & Inlay,
// both the Editor and the Inlay can be accepted as constructor arguments.
// There's no real need to supply Editor / Inlay as method parameters.
@SuppressWarnings("ALL") // The Inlay class is now considered "an experimental API" anyway
abstract class ElementRendererProxy implements EditorCustomElementRenderer {
    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        return calcWidthInPixels(inlay.getEditor());
    }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
        paint(inlay.getEditor(), g, r, textAttributes);
    }

    @Nullable
    @Override
    public String getContextMenuGroupId(@NotNull Inlay inlay) {
        return getContextMenuGroupId();
    }
}
