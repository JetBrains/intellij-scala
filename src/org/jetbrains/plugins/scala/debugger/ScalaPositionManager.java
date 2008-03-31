package org.jetbrains.plugins.scala.debugger;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JSR45PositionManager;
import com.intellij.debugger.engine.SourcesFinder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class ScalaPositionManager extends JSR45PositionManager {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.debugger.ScalaPositionManager");
  protected final String SCALA_FILE_SUFFIX = ".scala";
  private Project myProject;
  private static final String URL_PREFIX = "URL:";


  public ScalaPositionManager(DebugProcess debugProcess) {
    super(debugProcess,
            GlobalSearchScope.allScope(debugProcess.getProject()),
            DebugProcessImpl.JAVA_STRATUM,
            new LanguageFileType[]{ScalaFileType.SCALA_FILE_TYPE},
            new MySourceFinder());

    myProject = debugProcess.getProject();
  }


  protected String getGeneratedClassesPackage() {
    return "";
  }

  protected List getRelativeSourePathsByType(ReferenceType type) throws AbsentInformationException {
    String fileName = type.sourceName();
    String className = type.name();
    ArrayList<String> paths = new ArrayList<String>();
    if (fileName != null && className != null && fileName.endsWith(SCALA_FILE_SUFFIX)) {
      PsiFile[] files = FilenameIndex.getFilesByName(myProject, fileName, GlobalSearchScope.allScope(myDebugProcess.getProject()));
      for (PsiFile file : files) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (file instanceof ScalaFile && virtualFile != null) {
          for (PsiClass clazz : ((ScalaFile) file).getClasses()) {
            if (className.startsWith(clazz.getQualifiedName())) {
              String url = virtualFile.getUrl();
              paths.add(URL_PREFIX + url);
            }
          }
        }
      }
      return paths;
    } else {
      return super.getRelativeSourePathsByType(type);
    }
  }

  protected String getRelativeSourcePathByLocation(final Location location) throws AbsentInformationException {
    ReferenceType type = location.declaringType();
    String className = type.name();
    String fileName = type.sourceName();
    if (className != null && fileName != null && fileName.endsWith(SCALA_FILE_SUFFIX)) {
      PsiFile[] files = FilenameIndex.getFilesByName(myProject, fileName, GlobalSearchScope.allScope(myDebugProcess.getProject()));
      for (PsiFile file : files) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (file instanceof ScalaFile && virtualFile != null) {
          for (PsiClass clazz : ((ScalaFile) file).getClasses()) {
            if (className.startsWith(clazz.getQualifiedName())) {
              String url = virtualFile.getUrl();
              return URL_PREFIX + url;
            }
          }
        }
      }
    }
    return super.getRelativeSourcePathByLocation(location);
  }


  protected String getRelativePath(String sourcePath) {
    if (sourcePath != null) {
      sourcePath = sourcePath.trim();
    }
    return sourcePath;
  }

  private static class MySourceFinder implements SourcesFinder {

    public PsiFile findSourceFile(String relPath, Project project, Object o) {
      if (relPath.startsWith(URL_PREFIX)) {
        String url = relPath.substring(URL_PREFIX.length());
        VirtualFileManager fileManager = VirtualFileManager.getInstance();
        VirtualFile virtualFile = fileManager.findFileByUrl(url);
        if (virtualFile == null) return null;
        PsiManager manager = PsiManager.getInstance(project);
        return manager.findFile(virtualFile);
      } else {
        PsiFile[] files = FilenameIndex.getFilesByName(project, relPath, GlobalSearchScope.allScope(project));
        if (files.length > 0) return files[0];
        return null;
      }
    }
  }

  public DebugProcess getDebugProcess() {
    return myDebugProcess;
  }

}
