package org.jetbrains.plugins.scala.debugger.evaluateExpression;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerTestUtil;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.plugins.scala.compiler.CompilerProjectComponent;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author peter
 */
public abstract class ScalaCompilerTestCase extends JavaCodeInsightFixtureTestCase {
  private TempDirTestFixture myMainOutput;


  @Override
  protected void setUp() throws Exception {
    myMainOutput = new TempDirTestFixtureImpl();
    myMainOutput.setUp();
    super.setUp();
    getProject().getComponent(CompilerProjectComponent.class).projectOpened();
    CompilerManagerImpl.testSetup();

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(myMainOutput.findOrCreateDir("out").getUrl());
      }
    }.execute();
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk14Path().getPath());
  }

  @Override
  protected void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        try {
          myMainOutput.tearDown();
          myMainOutput = null;
          ScalaCompilerTestCase.super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  protected void setupTestSources() {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        final ContentEntry entry = rootModel.getContentEntries()[0];
        entry.removeSourceFolder(entry.getSourceFolders()[0]);
        entry.addSourceFolder(myFixture.getTempDirFixture().findOrCreateDir("src"), false);
        entry.addSourceFolder(myFixture.getTempDirFixture().findOrCreateDir("tests"), true);
        rootModel.commit();
      }
    }.execute();
  }

  protected void deleteClassFile(final String className) throws IOException {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final CompilerModuleExtension extension = ModuleRootManager.getInstance(myModule).getModuleExtension(CompilerModuleExtension.class);
        //noinspection ConstantConditions
        extension.getCompilerOutputPath().findChild(className + ".class").delete(this);
      }
    }.execute();
  }

  protected static void touch(VirtualFile file) throws IOException {
    file.setBinaryContent(file.contentsToByteArray(), file.getModificationStamp() + 1, file.getTimeStamp() + 1);
  }

  protected static void setFileText(final PsiFile file, final String barText) throws IOException {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        try {
          VfsUtil.saveText(ObjectUtils.assertNotNull(file.getVirtualFile()), barText);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  protected void setFileName(final PsiFile bar, final String name) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        bar.setName(name);
      }
    }.execute();
  }

  protected List<String> make() {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        try {
          CompilerTestUtil.disableExternalCompiler(getProject());
          CompilerManager.getInstance(getProject()).make(callback);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    //tests run in awt
    while (!semaphore.waitFor(100)) {
      if (SwingUtilities.isEventDispatchThread()) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    callback.throwException();
    return callback.getMessages();
  }

  protected void assertOutput(String className, String output) throws ExecutionException {
    assertOutput(className, output, myModule);
  }

  protected void assertOutput(String className, String expected, final Module module) throws ExecutionException {
    final StringBuffer sb = new StringBuffer();
    ProcessHandler process = runProcess(className, module, DefaultRunExecutor.class, new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        if (ProcessOutputTypes.SYSTEM != outputType) {
          sb.append(event.getText());
        }
      }
    }, ProgramRunner.PROGRAM_RUNNER_EP.findExtension(DefaultJavaProgramRunner.class));
    process.waitFor();
    assertEquals(expected.trim(), StringUtil.convertLineSeparators(sb.toString().trim()));
  }

  protected ProcessHandler runProcess(String className,
                                      Module module,
                                      final Class<? extends Executor> executorClass,
                                      final ProcessListener listener, final ProgramRunner runner) throws ExecutionException {
    final ApplicationConfiguration configuration = new ApplicationConfiguration("app", getProject(), ApplicationConfigurationType.getInstance());
    configuration.setModule(module);
    configuration.setMainClassName(className);
    final Executor executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(executorClass);
    ExecutionEnvironmentBuilder executionEnvironmentBuilder = new ExecutionEnvironmentBuilder(getProject(), executor);
    executionEnvironmentBuilder.setRunProfile(configuration);
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final AtomicReference<ProcessHandler> processHandler = new AtomicReference<ProcessHandler>();
    runner.execute(executionEnvironmentBuilder.build(), new ProgramRunner.Callback() {
      public void processStarted(final RunContentDescriptor descriptor) {
        disposeOnTearDown(new Disposable() {
          public void dispose() {
            descriptor.dispose();
          }
        });
        final ProcessHandler handler = descriptor.getProcessHandler();
        assert handler != null;
        handler.addProcessListener(listener);
        processHandler.set(handler);
        semaphore.up();
      }
    });
    semaphore.waitFor();
    return processHandler.get();
  }

  private static class ErrorReportingCallback implements CompileStatusNotification {
    private final Semaphore mySemaphore;
    private Throwable myError;
    private final List<String> myMessages = new ArrayList<String>();

    public ErrorReportingCallback(Semaphore semaphore) {
      mySemaphore = semaphore;
    }

    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      try {
        for (CompilerMessageCategory category : CompilerMessageCategory.values()) {
          for (CompilerMessage message : compileContext.getMessages(category)) {
            final String msg = message.getMessage();
            if (category != CompilerMessageCategory.INFORMATION || !msg.startsWith("Compilation completed successfully")) {
              myMessages.add(category + ": " + msg);
            }
          }
        }
        if (errors > 0) {
          fail("Compiler errors occurred! " + StringUtil.join(myMessages, "\n"));
        }
        assertFalse("Code did not compile!", aborted);
      }
      catch (Throwable t) {
        myError = t;
      }
      finally {
        mySemaphore.up();
      }
    }

    void throwException() {
      if (myError != null) {
        throw new RuntimeException(myError);
      }
    }

    public List<String> getMessages() {
      return myMessages;
    }
  }
}
