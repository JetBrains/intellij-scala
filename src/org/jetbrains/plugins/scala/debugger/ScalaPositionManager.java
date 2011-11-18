package org.jetbrains.plugins.scala.debugger;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.CompoundPositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassNotPreparedException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaLoader;
import org.jetbrains.plugins.scala.caches.ScalaCachesManager;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses;
import org.jetbrains.plugins.scala.lang.psi.api.expr.*;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public class ScalaPositionManager implements PositionManager {

  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.PositionManagerImpl");

  private static final String SCRIPT_HOLDER_CLASS_NAME = "Main$$anon$1";

  private final DebugProcess myDebugProcess;

  public ScalaPositionManager(DebugProcess debugProcess) {
    myDebugProcess = debugProcess;
  }

  public DebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @NotNull
  public List<Location> locationsOfLine(ReferenceType type,
                                        SourcePosition position) throws NoDataException {
    try {
      int line = position.getLine() + 1;
      List<Location> locations = getDebugProcess().getVirtualMachineProxy().versionHigher("1.4") ?
          type.locationsOfLine(DebugProcessImpl.JAVA_STRATUM, null, line) : type.locationsOfLine(line);
      if (locations == null || locations.isEmpty()) throw new NoDataException();
      return locations;
    }
    catch (AbsentInformationException e) {
      throw new NoDataException();
    }
  }

  private ScalaPsiElement findReferenceTypeSourceImage(SourcePosition position) {
    PsiFile file = position.getFile();
    if (!(file instanceof ScalaFile)) return null;
    PsiElement element = file.findElementAt(position.getOffset());
    if (element == null) return null;
    while (true) {
      if (element == null) break;
      if (element instanceof ScForStatement || element instanceof ScTypeDefinition || element instanceof ScFunctionExpr)
        break;
      if (element instanceof ScExtendsBlock && ((ScExtendsBlock) element).isAnonymousClass()) break;
      if (element instanceof ScCaseClauses && element.getParent() instanceof ScBlockExpr) break;
      if (element instanceof ScExpression) {
        if (ScalaPsiUtil.isByNameArgument((ScExpression) element)) {
          break;
        }
      }
      element = element.getParent();
    }

    return (ScalaPsiElement) element;
  }

  private ScTypeDefinition findEnclosingTypeDefinition(SourcePosition position) {
    PsiFile file = position.getFile();
    if (!(file instanceof ScalaFile)) return null;
    PsiElement element = file.findElementAt(position.getOffset());
    if (element == null) return null;
    return PsiTreeUtil.getParentOfType(element, ScTypeDefinition.class);
  }

  private static String getSpecificName(String name, Class<? extends PsiClass> clazzClass) {
    if (ScObject.class.isAssignableFrom(clazzClass)) return name + "$";
    if (ScTrait.class.isAssignableFrom(clazzClass)) return name + "$class";
    return name;
  }


  public ClassPrepareRequest createPrepareRequest(final ClassPrepareRequestor requestor, final SourcePosition position) throws NoDataException {
    final Ref<String> qName = new Ref<String>(null);
    final Ref<ClassPrepareRequestor> waitRequestor = new Ref<ClassPrepareRequestor>(null);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ScalaPsiElement sourceImage = ApplicationManager.getApplication().runReadAction(new Computable<ScalaPsiElement>() {
          public ScalaPsiElement compute() {
            return findReferenceTypeSourceImage(position);
          }
        });
        if (sourceImage instanceof ScTypeDefinition && !ScalaPsiUtil.isLocalClass((PsiClass) sourceImage)) {
          qName.set(getSpecificName(((ScTypeDefinition) sourceImage).getQualifiedNameForDebugger(), ((ScTypeDefinition) sourceImage).getClass()));
        } else if (sourceImage instanceof ScFunctionExpr ||
            sourceImage instanceof ScForStatement ||
            sourceImage instanceof ScExtendsBlock ||
            sourceImage instanceof ScCaseClauses && sourceImage.getParent() instanceof ScBlockExpr ||
            sourceImage instanceof ScExpression /*by name argument*/ ||
            sourceImage instanceof ScTypeDefinition) {
          ScTypeDefinition typeDefinition = findEnclosingTypeDefinition(position);
          if (typeDefinition != null) {
            final String fqn = typeDefinition.getQualifiedNameForDebugger();
            qName.set(fqn + "$*");
          }
        }
        // Enclosing closure not found
        if (qName.get() == null) {
          ScTypeDefinition typeDefinition = findEnclosingTypeDefinition(position);
          if (typeDefinition != null) {
            qName.set(getSpecificName(typeDefinition.getQualifiedNameForDebugger(), typeDefinition.getClass()));
          }
          final PsiFile file = position.getFile();
          if(file instanceof ScalaFile) {
            qName.set(SCRIPT_HOLDER_CLASS_NAME + "*");
          }
        }
        waitRequestor.set(new MyClassPrepareRequestor(position, requestor));      }
    });
    if (qName.get() == null) throw new NoDataException();
    if (waitRequestor.get() == null) throw new NoDataException();
    return myDebugProcess.getRequestsManager().createClassPrepareRequest(waitRequestor.get(), qName.get());
  }

  public SourcePosition getSourcePosition(final Location location) throws NoDataException {
    if (location == null) throw new NoDataException();

    PsiFile psiFile = getPsiFileByLocation(getDebugProcess().getProject(), location);
    if (psiFile == null) throw new NoDataException();

    int lineNumber = calcLineIndex(location);
    if (lineNumber < 0) throw new NoDataException();
    return SourcePosition.createFromLine(psiFile, lineNumber);
  }

  private int calcLineIndex(Location location) {
    LOG.assertTrue(myDebugProcess != null);
    if (location == null) return -1;

    try {
      return location.lineNumber() - 1;
    }
    catch (InternalError e) {
      return -1;
    }
  }

  @Nullable
  private PsiFile getPsiFileByLocation(final Project project, final Location location) {
    if (location == null) return null;

    final ReferenceType refType = location.declaringType();
    if (refType == null) return null;

    final String originalQName = refType.name().replace('/', '.');

    final GlobalSearchScope searchScope = myDebugProcess.getSearchScope();

    if(originalQName.startsWith(SCRIPT_HOLDER_CLASS_NAME)) {
      try {
        final String sourceName = location.sourceName();
        final PsiFile[] files = FilenameIndex.getFilesByName(project, sourceName, searchScope);
        if(files.length == 1) {
          return files[0];
        }
      } catch (AbsentInformationException e) {
        return null;
      }
    }

    int dollar = originalQName.indexOf('$');
    final String qName = dollar >= 0 ? originalQName.substring(0, dollar) : originalQName;

    final PsiClass[] classes = ScalaCachesManager.getInstance(project).getNamesCache().getClassesByFQName(qName, searchScope);
    PsiClass clazz = classes.length == 1 ? classes[0] : null;
    if (clazz != null && clazz.isValid()) {
      return clazz.getNavigationElement().getContainingFile();
    }

    DirectoryIndex directoryIndex = DirectoryIndex.getInstance(project);
    int dotIndex = qName.lastIndexOf(".");
    String packageName = dotIndex > 0 ? qName.substring(0, dotIndex) : "";
    Query<VirtualFile> query = directoryIndex.getDirectoriesByPackageName(packageName, true);
    String fileNameWithoutExtension = dotIndex > 0 ? qName.substring(dotIndex + 1) : qName;
    final Set<String> fileNames = new HashSet<String>();
    for (final String extention : ScalaLoader.SCALA_EXTENSIONS) {
      fileNames.add(fileNameWithoutExtension + "." + extention);
    }

    final Ref<PsiFile> result = new Ref<PsiFile>();
    query.forEach(new Processor<VirtualFile>() {
      public boolean process(VirtualFile vDir) {
        for (final String fileName : fileNames) {
          VirtualFile vFile = vDir.findChild(fileName);
          if (vFile != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
            if (psiFile instanceof ScalaFile) {
              result.set(psiFile);
              return false;
            }
          }
        }

        return true;
      }
    });

    return result.get();
  }

  @NotNull
  public List<ReferenceType> getAllClasses(final SourcePosition position) throws NoDataException {
    List<ReferenceType> result = ApplicationManager.getApplication().runReadAction(new Computable<List<ReferenceType>>() {
      public List<ReferenceType> compute() {
        ScalaPsiElement sourceImage = findReferenceTypeSourceImage(position);
        if (sourceImage instanceof ScTypeDefinition) {
          ScTypeDefinition definition = (ScTypeDefinition) sourceImage;
          String qName = getSpecificName(definition.getQualifiedNameForDebugger(), definition.getClass());
          if (qName != null) return myDebugProcess.getVirtualMachineProxy().classesByName(qName);
        } else {
          final ScTypeDefinition typeDefinition = findEnclosingTypeDefinition(position);
          String enclosingName = null;
          if (typeDefinition != null) {
            enclosingName = typeDefinition.getQualifiedNameForDebugger();
          }
          if (enclosingName != null) {
            final List<ReferenceType> outers = myDebugProcess.getVirtualMachineProxy().allClasses();
            final List<ReferenceType> result = new ArrayList<ReferenceType>(outers.size());
            for (ReferenceType outer : outers) {
              if (outer.name().startsWith(enclosingName)) {
                try {
                  if (outer.locationsOfLine(position.getLine() + 1).size() > 0) {
                    result.add(outer);
                  }
                } catch (AbsentInformationException ignore) {
                } catch (ClassNotPreparedException ignore) {
                }
              }
            }
            return result;
          }
        }
        return Collections.emptyList();
      }
    });

    if (result == null || result.isEmpty()) throw new NoDataException();
    return result;
  }

  //todo: this is possibly redundant method. (Copy paste from Java/Groovy)
  @Deprecated
  @Nullable
  private ReferenceType findNested(ReferenceType fromClass, final ScalaPsiElement toFind, SourcePosition classPosition) {
    final VirtualMachineProxy vmProxy = myDebugProcess.getVirtualMachineProxy();
    if (fromClass.isPrepared()) {

      final List<ReferenceType> nestedTypes = vmProxy.nestedTypes(fromClass);

      for (ReferenceType nested : nestedTypes) {
        final ReferenceType found = findNested(nested, toFind, classPosition);
        if (found != null) {
          return found;
        }
      }

      try {
        final int lineNumber = classPosition.getLine() + 1;
        if (fromClass.locationsOfLine(lineNumber).size() > 0) {
          return fromClass;
        }
        //noinspection LoopStatementThatDoesntLoop
        for (Location location : fromClass.allLineLocations()) {
          final SourcePosition candidateFirstPosition = SourcePosition.createFromLine(toFind.getContainingFile(), location.lineNumber() - 1);
          if (toFind.equals(findReferenceTypeSourceImage(candidateFirstPosition))) {
            return fromClass;
          }
          break; // check only the first location
        }
      }
      catch (AbsentInformationException ignored) {
      }
    }
    return null;
  }

  private static class MyClassPrepareRequestor implements ClassPrepareRequestor {
    private final SourcePosition position;
    private final ClassPrepareRequestor requestor;

    public MyClassPrepareRequestor(SourcePosition position, ClassPrepareRequestor requestor) {
      this.position = position;
      this.requestor = requestor;
    }

    public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
      final CompoundPositionManager positionManager = ((DebugProcessImpl) debuggerProcess).getPositionManager();
      if (positionManager.locationsOfLine(referenceType, position).size() > 0) {
        requestor.processClassPrepare(debuggerProcess, referenceType);
      } else {
        final List<ReferenceType> positionClasses = positionManager.getAllClasses(position);
        if (positionClasses.contains(referenceType)) {
          requestor.processClassPrepare(debuggerProcess, referenceType);
        }
      }
    }
  }
}
