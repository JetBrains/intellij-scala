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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaLoader;
import org.jetbrains.plugins.scala.caches.ScalaCachesManager;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForStatement;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr;
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
      if (element instanceof ScForStatement || element instanceof ScTypeDefinition || element instanceof ScFunctionExpr) break;
      if (element instanceof ScExtendsBlock && ((ScExtendsBlock) element).isAnonymousClass()) break;
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
    ScalaPsiElement sourceImage = findReferenceTypeSourceImage(position);
    String qName = null;
    if (sourceImage instanceof ScTypeDefinition) {
      qName = getSpecificName(((ScTypeDefinition) sourceImage).getQualifiedName(), ((ScTypeDefinition) sourceImage).getClass());
    } else if (sourceImage instanceof ScFunctionExpr || sourceImage instanceof ScForStatement
        || sourceImage instanceof ScExtendsBlock) {
      ScTypeDefinition typeDefinition = findEnclosingTypeDefinition(position);
      if (typeDefinition != null) {
        qName = getSpecificName(typeDefinition.getQualifiedName(), typeDefinition.getClass()) + "*";
      }
    }
    // Enclosinc closure not found
    if (qName == null) {
      ScTypeDefinition typeDefinition = findEnclosingTypeDefinition(position);
      if (typeDefinition != null) {
        qName = getSpecificName(typeDefinition.getQualifiedName(), typeDefinition.getClass());
      }
      if (qName == null) throw new NoDataException();
    }


    ClassPrepareRequestor waitRequestor = new MyClassPrepareRequestor(position, requestor);
    return myDebugProcess.getRequestsManager().createClassPrepareRequest(waitRequestor, qName);
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
    int dollar = originalQName.indexOf('$');
    final String qName = dollar >= 0 ? originalQName.substring(0, dollar) : originalQName;
    final GlobalSearchScope searchScope = myDebugProcess.getSearchScope();

    final PsiClass[] classes = project.getComponent(ScalaCachesManager.class).getNamesCache().getClassesByFQName(qName, searchScope);
    PsiClass clazz = classes.length == 1 ? classes[0] : null;
    if (clazz != null && clazz.isValid()) return clazz.getContainingFile();

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
          String qName = getSpecificName(definition.getQualifiedName(), definition.getClass());
          if (qName != null) return myDebugProcess.getVirtualMachineProxy().classesByName(qName);
        } else {
          final ScTypeDefinition typeDefinition = findEnclosingTypeDefinition(position);
          String enclosingName = null;
          if (typeDefinition != null) {
            enclosingName = typeDefinition.getQualifiedName();
          }
          if (enclosingName != null) {
            String specificName = getSpecificName(enclosingName, typeDefinition.getClass());
            final List<ReferenceType> outers = myDebugProcess.getVirtualMachineProxy().classesByName(specificName);
            final List<ReferenceType> result = new ArrayList<ReferenceType>(outers.size());
            for (ReferenceType outer : outers) {
              final ReferenceType nested = findNested(outer, sourceImage, position);
              if (nested != null) {
                result.add(nested);
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
