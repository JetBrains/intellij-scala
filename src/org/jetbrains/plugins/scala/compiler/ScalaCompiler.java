package org.jetbrains.plugins.scala.compiler;

import com.intellij.compiler.CompilerException;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.BackendCompilerWrapper;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.config.ScalaFacet;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import java.util.Arrays;

/**
 * @author ven, ilyas
 */
public class ScalaCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.compiler.ScalaCompiler");
  private Project myProject;
  private static final FileTypeManager FILE_TYPE_MANAGER = FileTypeManager.getInstance();

  public ScalaCompiler(Project project) {
    myProject = project;
  }

  @NotNull
  public String getDescription() {
    return ScalaBundle.message("scala.compiler.description");
  }

  public boolean isCompilableFile(final VirtualFile file, CompileContext context) {

    // Do not run compiler for pure Java projects
    if (!isScalaProject()) return false;

    // Check for compiler existence
    final FileType fileType = FILE_TYPE_MANAGER.getFileTypeByFile(file);
    final PsiFile psi = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      public PsiFile compute() {
        return PsiManager.getInstance(myProject).findFile(file);
      }
    });

    Module module = context.getModuleByFile(file);

    class BooleanWrapper{public boolean re = false;}
    final BooleanWrapper b = ApplicationManager.getApplication().runReadAction(new Computable<BooleanWrapper>() {
      public BooleanWrapper compute() {
        BooleanWrapper b = new BooleanWrapper();
        b.re = fileType.equals(ScalaFileType.SCALA_FILE_TYPE) && psi instanceof ScalaFile && !((ScalaFile) psi).isScriptFile(true);
        return b;
      }
    });
    boolean notScript = b.re;
    return notScript ||
        context.getProject() != null &&
            fileType.equals(StdFileTypes.JAVA) &&
            ScalacSettings.getInstance(context.getProject()).SCALAC_BEFORE &&
            module != null &&
            ScalaUtils.isSuitableModule(module) &&
            isScalaModule(module);
  }

  private boolean isScalaProject() {
    final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
    boolean isScalaProject = false;
    for (Module module : allModules) {
      if (isScalaModule(module)) {
        isScalaProject = true;
        break;
      }
    }
    return isScalaProject;
  }

  private static boolean isScalaModule(Module module) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    return facetManager.getFacetByType(ScalaFacet.ID) != null;
  }

  public void compile(CompileContext context, Chunk<Module> moduleChunk, VirtualFile[] files, OutputSink sink) {
        final BackendCompiler backEndCompiler = getBackEndCompiler();
    final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(moduleChunk, myProject, Arrays.asList(files),
        (CompileContextEx) context, backEndCompiler, sink);
    try {
      wrapper.compile();
    }
    catch (CompilerException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (CacheCorruptedException e) {
      LOG.info(e);
      context.requestRebuildNextTime(e.getMessage());
    }

  }

  public boolean validateConfiguration(CompileScope scope) {
    return getBackEndCompiler().checkCompiler(scope);
  }

  private BackendCompiler getBackEndCompiler() {
    return new ScalacBackendCompiler(myProject);
  }
}