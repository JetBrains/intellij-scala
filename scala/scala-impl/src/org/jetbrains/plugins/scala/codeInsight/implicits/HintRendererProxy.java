package org.jetbrains.plugins.scala.codeInsight.implicits;

import com.intellij.codeInsight.daemon.impl.HintRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

// A workaround for the raw Inlay Java types.
// Given that a renderer is created in the context of Editor & Inlay,
// both the Editor and the Inlay can be accepted as constructor arguments.
// There's no real need to supply Editor / Inlay as method parameters.
abstract class HintRendererProxy extends HintRenderer {
    public HintRendererProxy(String text) {
        super(text);
    }

    @Override
    public int calcWidthInPixels(Inlay inlay) {
        return calcWidthInPixels(inlay.getEditor());
    }

    @Override
    public void paint(Inlay inlay, Graphics g, Rectangle r, TextAttributes textAttributes) {
        paint(inlay.getEditor(), g, r, textAttributes);
    }

    @Nullable
    @Override
    public String getContextMenuGroupId(@NotNull Inlay inlay) {
        return getContextMenuGroupId();
    }
}
