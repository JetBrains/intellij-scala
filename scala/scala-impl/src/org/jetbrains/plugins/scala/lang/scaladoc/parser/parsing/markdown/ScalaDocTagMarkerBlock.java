package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown;

import org.intellij.markdown.IElementType;
import org.intellij.markdown.MarkdownElementType;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MarkdownCompanionProxy;

public class ScalaDocTagMarkerBlock extends MarkerBlockImpl {
    public static final MarkdownElementType TAG_BLOCK = new MarkdownElementType("TAG_BLOCK", false);
    public static final MarkdownElementType TAG_NAME = new MarkdownElementType("TAG_NAME", true);
    public static final MarkdownElementType TAG_ARGUMENT = new MarkdownElementType("TAG_ARGUMENT", false);

    public ScalaDocTagMarkerBlock(@NotNull MarkdownConstraints constraints, @NotNull ProductionHolder.Marker marker) {
        super(constraints, marker);
    }

    @Override
    protected int calcNextInterestingOffset(@NotNull LookaheadText.Position position) {
        // We're interested in the next line to check for tags
        return position.getNextLineOrEofOffset();
    }

    @Override
    protected @NotNull ClosingAction getDefaultAction() {
        return ClosingAction.DONE;
    }

    @Override
    protected @NotNull ProcessingResult doProcessToken(@NotNull LookaheadText.Position position, @NotNull MarkdownConstraints markdownConstraints) {
        // Tags are the first thing on the line
        if (position.getOffsetInCurrentLine() != -1) {
            return MarkdownCompanionProxy.MarkerBlockProcessingResultCompanion.getPASS();
        }

        CharSequence currentLine = position.getCurrentLine();

        // Tags accept any amount of whitespace at the start of the line
        int start = 0;
        while (start < currentLine.length() && Character.isWhitespace(currentLine.charAt(start))) {
            start++;
        }

        if (start < currentLine.length() && currentLine.charAt(start) == '@') {
            // There's another tag here; close
            return MarkdownCompanionProxy.MarkerBlockProcessingResultCompanion.getDEFAULT();
        } else {
            return MarkdownCompanionProxy.MarkerBlockProcessingResultCompanion.getPASS();
        }
    }

    @Override
    public @NotNull IElementType getDefaultNodeType() {
        return TAG_BLOCK;
    }

    @Override
    public boolean isInterestingOffset(@NotNull LookaheadText.Position position) {
        return position.getOffsetInCurrentLine() != -1;
    }

    @Override
    public boolean allowsSubBlocks() {
        return true;
    }
}
