package org.jetbrains.plugins.scala.debugger;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JSR45PositionManager;
import com.intellij.debugger.engine.SourcesFinder;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;

import java.util.ArrayList;
import java.util.Iterator;
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

  @NotNull
  public List<ReferenceType> getAllClasses(SourcePosition classPosition) throws NoDataException {
    List<ReferenceType> referenceTypes = super.getAllClasses(classPosition);
    if (referenceTypes.size() > 1 && classPosition.getFile() instanceof ScalaFile) {
      return filterReferences(referenceTypes, classPosition);
    }
    return referenceTypes;
  }

  private static List<ReferenceType> filterReferences(List<ReferenceType> referenceTypes, SourcePosition classPosition) {
    PsiFile file = classPosition.getFile();
    if (!(file instanceof ScalaFile)) return referenceTypes;
    ScalaFile scalaFile = (ScalaFile) file;
    PsiElement element = scalaFile.findElementAt(classPosition.getOffset());
    while (element != null && !(element instanceof PsiClass)) {
      element = element.getParent();
    }
    if (element == null) return referenceTypes;
    PsiClass clazz = (PsiClass) element;
    String name = clazz.getQualifiedName();

    Iterator<ReferenceType> iterator = referenceTypes.iterator();
    while (iterator.hasNext()) {
      ReferenceType type = iterator.next();
      if (!(getSpecificName(name, clazz.getClass())).equals(type.name())) {
        iterator.remove();
      }
    }

    return referenceTypes;
  }

  private static String getSpecificName(String name, Class<? extends PsiClass> clazzClass) {
    if (ScObject.class.isAssignableFrom(clazzClass)) return name + "$";
    if (ScTrait.class.isAssignableFrom(clazzClass)) return name + "$class";
    return name;
  }

  protected void onClassPrepare(final DebugProcess debuggerProcess, final ReferenceType referenceType,
                                final SourcePosition position, final ClassPrepareRequestor requestor) {
    final PsiFile file = position.getFile();
    if (file instanceof ScalaFile) {
      Runnable runnable = new Runnable() {
        public void run() {
          PsiElement element = file.findElementAt(position.getOffset());
          while (element != null && !(element instanceof PsiClass)) {
            element = element.getParent();
          }
          if (element == null) return;
          PsiClass clazz = (PsiClass) element;
          if (referenceType.name().startsWith(getSpecificName(clazz.getQualifiedName(), clazz.getClass()))) {
            requestor.processClassPrepare(debuggerProcess, referenceType);
          }
        }
      };
      PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(runnable);
    } else {
      super.onClassPrepare(debuggerProcess, referenceType, position, requestor);
    }
  }

  public DebugProcess getDebugProcess() {
    return myDebugProcess;
  }

}
