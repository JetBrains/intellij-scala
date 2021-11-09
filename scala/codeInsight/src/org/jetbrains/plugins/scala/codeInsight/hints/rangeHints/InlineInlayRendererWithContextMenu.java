package org.jetbrains.plugins.scala.codeInsight.hints.rangeHints;

import com.intellij.codeInsight.hints.HorizontalConstrainedPresentation;
import com.intellij.codeInsight.hints.HorizontalConstraints;
import com.intellij.codeInsight.hints.LinearOrderInlayRenderer;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.openapi.editor.Inlay;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

// TODO: Maybe we should have another design for the context menu of our inlay hints.
//       For example like in kotlin where there is a disable menu-entry to disable the clicked hint
public class InlineInlayRendererWithContextMenu extends LinearOrderInlayRenderer<HorizontalConstraints> {
    private String contextMenu;

    public InlineInlayRendererWithContextMenu(HorizontalConstrainedPresentation<InlayPresentation> presentation, String contextMenu) {
        super(
                Collections.singletonList(presentation),
                x -> x.get(0).getRoot(),
                (x, y) -> 0
        );
        this.contextMenu = contextMenu;
    }

    public boolean isAcceptablePlacement(Inlay.Placement placement) {
        return placement == Inlay.Placement.INLINE;
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        return super.calcWidthInPixels(inlay);
    }

    @Override
    public String getContextMenuGroupId(Inlay inlay) {
        return contextMenu;
    }
}
