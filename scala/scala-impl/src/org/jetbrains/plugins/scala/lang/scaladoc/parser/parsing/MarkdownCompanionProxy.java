package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing;

import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;

public class MarkdownCompanionProxy {
    public final static CommonMarkdownConstraints.Companion CommonMarkdownConstraintsCompanion = CommonMarkdownConstraints.Companion;
    public final static MarkerBlockProvider.Companion MarkerBlockProviderCompanion = MarkerBlockProvider.Companion;
    public final static MarkerBlock.ProcessingResult.Companion MarkerBlockProcessingResultCompanion = MarkerBlock.ProcessingResult.Companion;
}
