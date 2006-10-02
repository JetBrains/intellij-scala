package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.09.2006
 * Time: 12:58:17
 */
public class ScalaCompiler implements TranslatingCompiler{

    public boolean isCompilableFile(VirtualFile virtualFile, CompileContext compileContext) {
        return "scala".equals(virtualFile.getFileType().getDefaultExtension()) && !virtualFile.isDirectory();
    }

    class ScalaCompileExitStatus implements ExitStatus {
        public OutputItem[] getSuccessfullyCompiled() {
            return new OutputItem[0];
        }

        public VirtualFile[] getFilesToRecompile() {
            return new VirtualFile[0];
        }
    }

    public ExitStatus compile(CompileContext compileContext, VirtualFile[] virtualFiles) {
        //todo: compile: java -cp scala-compiler.jar  scala.tools.nsc.Main file.scala; check for errors
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.addParameter("scalac");

        return new ScalaCompileExitStatus();
    }

    @NotNull
    public String getDescription() {
        return null;  
    }

    public boolean validateConfiguration(CompileScope compileScope) {
        return false;
    }
}