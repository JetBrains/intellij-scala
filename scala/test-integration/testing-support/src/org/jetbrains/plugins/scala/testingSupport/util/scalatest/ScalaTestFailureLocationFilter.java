package org.jetbrains.plugins.scala.testingSupport.util.scalatest;

import com.intellij.execution.filters.ExceptionInfoCache;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoFactory;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;


/**
 * A custom console output filter to build hyperlinks to exact test failure location in ScalaTest.<br>
 * Pretty much copies {@link com.intellij.execution.filters.ExceptionFilter}.
 */
public class ScalaTestFailureLocationFilter implements Filter {

  private final ExceptionInfoCache myCache;

//  private final static Pattern fqnPattern = Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)+\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

  //See org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporterWithLocation.apply
  private final static String testFailureLocationPrefix = "ScalaTestFailureLocation: ";

  public ScalaTestFailureLocationFilter(@NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(project, scope);
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    Project myProject = myCache.getProject();
    if (!line.startsWith(testFailureLocationPrefix)) return null;
    final int atKeywordLocation = line.indexOf(" at ");
    if (atKeywordLocation < 0) return null;
    //get class name and make sure it is a fqn
//    if (!fqnPattern.matcher(className).matches()) return null;
    int lparenthIndex = line.lastIndexOf("(");
    int rparenthIndex = line.lastIndexOf(")");
    if (lparenthIndex < 0 || rparenthIndex < 0) return null;
    String fileAndLine = line.substring(lparenthIndex + 1, rparenthIndex);
    int colonIndex = fileAndLine.lastIndexOf(":");
    if (colonIndex < 0) return null;
    int lineNumber = ScalaTestFailureLocationFilter.getLineNumber(fileAndLine.substring(colonIndex + 1));
    if (lineNumber < 0) return null;
    final var fileName = fileAndLine.substring(0, colonIndex).trim();

    //NOTE: class name can be empty (see examples in SCL-21627 and https://github.com/scalatest/scalatest/issues/2286)
    //in this case only file name will be used during resolution under the hood
    final String className = line.substring(testFailureLocationPrefix.length(), atKeywordLocation).trim();
    final var resolutionResult = myCache.resolveClassOrFile(className, fileName);

    final var virtualFiles = resolutionResult.getClasses().keySet().stream().toList();
    if (virtualFiles.isEmpty())
      return null;

    final int textStartOffset = entireLength - line.length();

    final int highlightStartOffset = textStartOffset + lparenthIndex + 1;
    final int highlightEndOffset = textStartOffset + rparenthIndex;

    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
    if (resolutionResult.isInLibrary()) {
      Color libTextColor = UIUtil.getInactiveTextColor();
      attributes = attributes.clone();
      attributes.setForegroundColor(libTextColor);
      attributes.setEffectColor(libTextColor);
    }
    HyperlinkInfo linkInfo = HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(virtualFiles, lineNumber - 1, myProject);
    return new Filter.Result(highlightStartOffset, highlightEndOffset, linkInfo, attributes);
  }

  private static int getLineNumber(String lineString) {
    // some quick checks to avoid costly exceptions
    if (lineString.isEmpty() || lineString.length() > 9 || !Character.isDigit(lineString.charAt(0))) {
      return -1;
    }

    try {
      return Integer.parseInt(lineString);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
