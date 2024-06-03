package org.jetbrains.jps.incremental.scala.data;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

// The code in this class is copied from `org.jetbrains.jps.incremental.java.JavaBuilder`. It replicates the per-module
// setup of javacOptions.
final class JavacOptionsProvider {
    private JavacOptionsProvider() {}

    private static final String COMPILER_NAME = "java";

    private static final String SOURCE_OPTION = "-source";
    private static final String TARGET_OPTION = "-target";
    private static final String RELEASE_OPTION = "--release";
    private static final String PROC_NONE_OPTION = "-proc:none";
    private static final String PROC_ONLY_OPTION = "-proc:only";
    private static final String PROCESSORPATH_OPTION = "-processorpath";

    private static final Set<String> FILTERED_OPTIONS = ContainerUtil.newHashSet(
            TARGET_OPTION, RELEASE_OPTION, "-d"
    );
    private static final Set<String> FILTERED_SINGLE_OPTIONS = ContainerUtil.newHashSet(
            "-g", "-deprecation", "-nowarn", "-verbose", PROC_NONE_OPTION, PROC_ONLY_OPTION, "-proceedOnError"
    );
    private static final Set<String> POSSIBLY_CONFLICTING_OPTIONS = ContainerUtil.newHashSet(
            SOURCE_OPTION, "--boot-class-path", "-bootclasspath", "--class-path", "-classpath", "-cp", PROCESSORPATH_OPTION, "-sourcepath", "--module-path", "-p", "--module-source-path"
    );

    // Copied with minor modifications from `org.jetbrains.jps.incremental.java.JavaBuilder.getCompilationOptions`
    // which is a private method.
    static void addCommonJavacOptions(List<String> options, JpsJavaCompilerOptions compilerOptions, CompileContext context, ModuleChunk chunk) {
        if (compilerOptions.DEBUGGING_INFO) {
            options.add("-g");
        }
        if (compilerOptions.DEPRECATION) {
            options.add("-deprecation");
        }
        if (compilerOptions.GENERATE_NO_WARNINGS) {
            options.add("-nowarn");
        }

        String customArgs = compilerOptions.ADDITIONAL_OPTIONS_STRING;
        final Map<String, String> overrideMap = compilerOptions.ADDITIONAL_OPTIONS_OVERRIDE;
        if (!overrideMap.isEmpty()) {
            for (JpsModule m : chunk.getModules()) {
                final String overridden = overrideMap.get(m.getName());
                if (overridden != null) {
                    customArgs = overridden;
                    break;
                }
            }
        }

        if (customArgs != null && !customArgs.isEmpty()) {
            BiConsumer<List<String>, String> appender = List::add;
            final JpsModule module = chunk.representativeTarget().getModule();
            final File baseDirectory = JpsModelSerializationDataService.getBaseDirectory(module);
            if (baseDirectory != null) {
                //this is a temporary workaround to allow passing per-module compiler options for Eclipse compiler in form
                // -properties $MODULE_DIR$/.settings/org.eclipse.jdt.core.prefs
                final String moduleDirPath = FileUtil.toCanonicalPath(baseDirectory.getAbsolutePath());
                appender = (strings, option) -> strings.add(StringUtil.replace(option, PathMacroUtil.DEPRECATED_MODULE_DIR, moduleDirPath));
            }

            boolean skip = false;
            for (final String userOption : ParametersListUtil.parse(customArgs)) {
                if (FILTERED_OPTIONS.contains(userOption)) {
                    skip = true;
                    notifyOptionIgnored(context, userOption, chunk);
                    continue;
                }
                if (skip) {
                    skip = false;
                }
                else {
                    if (!FILTERED_SINGLE_OPTIONS.contains(userOption)) {
                        if (POSSIBLY_CONFLICTING_OPTIONS.contains(userOption)) {
                            notifyOptionPossibleConflicts(context, userOption, chunk);
                        }
                        appender.accept(options, userOption);
                    }
                    else {
                        notifyOptionIgnored(context, userOption, chunk);
                    }
                }
            }
        }
    }

    private static void notifyOptionIgnored(CompileContext context, String option, ModuleChunk chunk) {
        notifyMessage(context, BuildMessage.Kind.JPS_INFO, "build.message.user.specified.option.0.is.ignored.for.1", option, chunk.getPresentableShortName());
    }

    private static void notifyOptionPossibleConflicts(CompileContext context, String option, ModuleChunk chunk) {
        notifyMessage(context, BuildMessage.Kind.JPS_INFO, "build.message.user.specified.option.0.for.1.may.conflict.with.calculated.option", option, chunk.getPresentableShortName());
    }

    private static void notifyMessage(CompileContext context, final BuildMessage.Kind kind, final String messageKey, Object... params) {
        context.processMessage(new CompilerMessage("java", kind, JpsBuildBundle.message(messageKey, params)));
    }
}
