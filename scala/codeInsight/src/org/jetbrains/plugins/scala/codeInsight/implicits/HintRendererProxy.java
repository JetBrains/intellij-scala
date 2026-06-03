package org.jetbrains.plugins.scala.codeInsight.implicits;

import com.intellij.codeInsight.daemon.impl.HintRenderer;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.Editor;
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
abstract class HintRendererProxy extends HintRenderer {
    public HintRendererProxy(String text) {
        super(text);
    }

    protected abstract int calcWidthInPixels0(@NotNull Editor editor);

    @Override
    final public int calcWidthInPixels(@NotNull Inlay inlay) {
        return calcWidthInPixels0(inlay.getEditor());
    }

    protected abstract void paint0(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes);

    @Override
    final public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
        paint0(inlay.getEditor(), g, r, textAttributes);
    }

    @Nullable
    protected abstract String getContextMenuGroupId0(@NotNull Editor editor);

    @Nullable
    @Override
    final public String getContextMenuGroupId(@NotNull Inlay inlay) {
        return getContextMenuGroupId0(inlay.getEditor());
    }

    @Nullable
    protected abstract ActionGroup getContextMenuGroup0(@NotNull Editor editor);

    @Nullable
    @Override
    final public ActionGroup getContextMenuGroup(@NotNull Inlay inlay) {
        return getContextMenuGroup0(inlay.getEditor());
    }
}
