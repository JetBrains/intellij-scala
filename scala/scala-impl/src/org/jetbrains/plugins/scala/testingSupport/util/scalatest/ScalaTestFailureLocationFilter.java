package org.jetbrains.plugins.scala.testingSupport.util.scalatest;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoFactory;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.reference.SoftReference;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * A custom console outpit filter to build hyperlinks to exact test failure location in ScalaTest. Pretty much copies
 * {@link com.intellij.execution.filters.ExceptionFilter}.
 */
public class ScalaTestFailureLocationFilter implements Filter {

  private final ExceptionInfoCache myCache;

//  private final static Pattern fqnPattern = Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)+\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");
  private final static String testFailureLocationPrefix = "ScalaTestFailureLocation: ";

  public ScalaTestFailureLocationFilter(@NotNull final GlobalSearchScope scope) {
    myCache = new ExceptionInfoCache(scope);
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    Project myProject = myCache.getProject();
    if (!line.startsWith(testFailureLocationPrefix)) return null;
    int atKeywordLocation = line.indexOf(" at ");
    if (atKeywordLocation < 0) return null;
    //get class name and make sure it is a fqn
    String className = line.substring(testFailureLocationPrefix.length(), atKeywordLocation);
//    if (!fqnPattern.matcher(className).matches()) return null;
    int lparenthIndex = line.lastIndexOf("(");
    int rparenthIndex = line.lastIndexOf(")");
    if (lparenthIndex < 0 || rparenthIndex < 0) return null;
    String fileAndLine = line.substring(lparenthIndex + 1, rparenthIndex);
    int colonIndex = fileAndLine.lastIndexOf(":");
    if (colonIndex < 0) return null;
    int lineNumber = ScalaTestFailureLocationFilter.getLineNumber(fileAndLine.substring(colonIndex + 1));
    if (lineNumber < 0) return null;
    Pair<PsiClass[], PsiFile[]> classesAndFiles = myCache.resolveClass(className);
    PsiClass[] classes = classesAndFiles.first;
    PsiFile[] myFiles = classesAndFiles.second;
    if (myFiles.length == 0) {
      // try find the file with the required name
      //todo[nik] it would be better to use FilenameIndex here to honor the scope by it isn't accessible in Open API
      myFiles = PsiShortNamesCache.getInstance(myProject).getFilesByName(fileAndLine.substring(0, colonIndex).trim());
    }
    if (myFiles.length == 0) return null;

    final int textStartOffset = entireLength - line.length();

    final int highlightStartOffset = textStartOffset + lparenthIndex + 1;
    final int highlightEndOffset = textStartOffset + rparenthIndex;

    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    List<VirtualFile> virtualFilesInLibraries = new ArrayList<VirtualFile>();
    List<VirtualFile> virtualFilesInContent = new ArrayList<VirtualFile>();
    for (PsiFile file : myFiles) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (index.isInContent(virtualFile)) {
        virtualFilesInContent.add(virtualFile);
      }
      else {
        virtualFilesInLibraries.add(virtualFile);
      }
    }

    List<VirtualFile> virtualFiles;
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
    if (virtualFilesInContent.isEmpty()) {
      Color libTextColor = UIUtil.getInactiveTextColor();
      attributes = attributes.clone();
      attributes.setForegroundColor(libTextColor);
      attributes.setEffectColor(libTextColor);

      virtualFiles = virtualFilesInLibraries;
    }
    else {
      virtualFiles = virtualFilesInContent;
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

  //TODO: remove this hack
  /**
   * Copy of {@link com.intellij.execution.filters.ExceptionInfoCache}. Will remove it once the resolve method is made
   * public.
   */
  private static class ExceptionInfoCache {
    private final ConcurrentMap<String, SoftReference<Pair<PsiClass[], PsiFile[]>>> myCache = new ConcurrentHashMap<>();
    private final Project myProject;
    private final GlobalSearchScope mySearchScope;

    public ExceptionInfoCache(GlobalSearchScope searchScope) {
      myProject = Objects.requireNonNull(searchScope.getProject());
      mySearchScope = searchScope;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    private PsiClass[] findClassesPreferringMyScope(String className) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      PsiClass[] result = psiFacade.findClasses(className, mySearchScope);
      return result.length != 0 ? result : psiFacade.findClasses(className, GlobalSearchScope.allScope(myProject));
    }

    Pair<PsiClass[], PsiFile[]> resolveClass(String className) {
      Pair<PsiClass[], PsiFile[]> cached = SoftReference.dereference(myCache.get(className));
      if (cached != null) {
        return cached;
      }

      PsiClass[] classes = findClassesPreferringMyScope(className);
      if (classes.length == 0) {
        final int dollarIndex = className.indexOf('$');
        if (dollarIndex >= 0) {
          classes = findClassesPreferringMyScope(className.substring(0, dollarIndex));
        }
      }

      PsiFile[] files = new PsiFile[classes.length];
      for (int i = 0; i < classes.length; i++) {
        files[i] = (PsiFile) classes[i].getContainingFile().getNavigationElement();
      }

      Pair<PsiClass[], PsiFile[]> result = Pair.create(classes, files);
      myCache.put(className, new SoftReference<Pair<PsiClass[], PsiFile[]>>(result));
      return result;
    }
  }
}
