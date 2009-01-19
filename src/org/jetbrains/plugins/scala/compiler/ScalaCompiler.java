package org.jetbrains.plugins.scala.compiler;

import com.intellij.compiler.CompilerException;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.BackendCompilerWrapper;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.config.ScalaFacet;

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

    public boolean isCompilableFile(VirtualFile file, CompileContext context) {
      final FileType fileType = FILE_TYPE_MANAGER.getFileTypeByFile(file);

      Module module = context.getModuleByFile(file);
      return fileType.equals(ScalaFileType.SCALA_FILE_TYPE) ||
               context.getProject() != null &&
                   fileType.equals(StdFileTypes.JAVA) &&
                   ScalacSettings.getInstance(context.getProject()).SCALAC_BEFORE &&
                   module != null &&
                   (module.getModuleType() instanceof JavaModuleType || "PLUGIN_MODULE".equals(module.getModuleType().getId())) &&
                   isScalaModule(module);
    }

  private static boolean isScalaModule(Module module) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    return facetManager.getFacetByType(ScalaFacet.ID)  != null;
  }

  public ExitStatus compile(CompileContext context, VirtualFile[] files) {
        final BackendCompiler backEndCompiler = getBackEndCompiler();
        final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(myProject, files, (CompileContextEx) context, backEndCompiler);
        OutputItem[] outputItems;
        try {
            outputItems = wrapper.compile();
        }
        catch (CompilerException e) {
            outputItems = EMPTY_OUTPUT_ITEM_ARRAY;
            context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
        }
        catch (CacheCorruptedException e) {
            LOG.info(e);
            context.requestRebuildNextTime(e.getMessage());
            outputItems = EMPTY_OUTPUT_ITEM_ARRAY;
        }

        return new ExitStatusImpl(outputItems, wrapper.getFilesToRecompile());
    }

    public boolean validateConfiguration(CompileScope scope) {
        return getBackEndCompiler().checkCompiler(scope);
    }

    private BackendCompiler getBackEndCompiler() {
        return new ScalacCompiler(myProject);
    }

    private static class ExitStatusImpl implements ExitStatus {

        private OutputItem[] myOuitputItems;
        private VirtualFile[] myMyFilesToRecompile;

        public ExitStatusImpl(OutputItem[] ouitputItems, VirtualFile[] myFilesToRecompile) {
            myOuitputItems = ouitputItems;
            myMyFilesToRecompile = myFilesToRecompile;
        }

        public OutputItem[] getSuccessfullyCompiled() {
            return myOuitputItems;
        }

        public VirtualFile[] getFilesToRecompile() {
            return myMyFilesToRecompile;
        }
    }
}