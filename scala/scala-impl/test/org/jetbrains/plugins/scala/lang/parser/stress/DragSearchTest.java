package org.jetbrains.plugins.scala.lang.parser.stress;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.junit.Assert;

public class DragSearchTest extends TestCase {
    private static final int MAX_ROLLBACKS = 30;

    private static void exploreForDrags(Pair<TextRange, Integer>[] dragInfo) {
        int ourMaximum = max(dragInfo);
        boolean notFound = ContainerUtil.findAll(dragInfo, pair -> pair.getSecond() >= MAX_ROLLBACKS).isEmpty();

        if (!notFound) {
            Assert.assertTrue("Too much rollbacks: " + ourMaximum, ourMaximum < MAX_ROLLBACKS);
        }

    }

    private static int max(Pair<TextRange, Integer>[] dragInfo) {
        int max = 0;
        for (Pair<TextRange, Integer> pair : dragInfo) {
            if (pair.getSecond() > max) {
                max = pair.getSecond();
            }
        }
        return max;
    }

    public static Test suite() {
        return new ScalaFileSetTestCase("/parser/stress/data/") {
            @Override
            protected void runTest(@NotNull String testName,
                                   @NotNull String content,
                                   @NotNull Project project) {
                transform(testName, content, project);
            }

            @Override
            @NotNull
            protected String transform(@NotNull String testName,
                                       @NotNull String fileText,
                                       @NotNull Project project) {
                ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage());
                PsiBuilder psiBuilder = PsiBuilderFactory.getInstance().createBuilder(
                        parserDefinition,
                        parserDefinition.createLexer(project),
                        fileText
                );
                DragBuilderWrapper dragBuilder = new DragBuilderWrapper(project, psiBuilder);
                parserDefinition.createParser(project).parse(parserDefinition.getFileNodeType(), dragBuilder);

                Pair<TextRange, Integer>[] dragInfo = dragBuilder.getDragInfo();
                exploreForDrags(dragInfo);

                return super.transform(testName, fileText, project);
            }
        };
    }
}
