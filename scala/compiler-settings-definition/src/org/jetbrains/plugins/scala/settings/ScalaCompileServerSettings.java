package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.incremental.IncProjectBuilder;

import java.util.UUID;

@State(
        name = "ScalaSettings",
        storages = {@Storage("scala.xml")},
        reportStatistic = true,
        category = SettingsCategory.TOOLS
)
public class ScalaCompileServerSettings implements PersistentStateComponent<ScalaCompileServerSettings> {
  //ATTENTION: these field names should be the same as in
  //org.jetbrains.jps.incremental.scala.model.impl.GlobalSettingsImpl.State (see it's JavaDoc)
  public boolean COMPILE_SERVER_ENABLED = true;
  public String COMPILE_SERVER_SDK;

  //is not accessible from UI, but is serialized and used in jps-plugin
  public String COMPILE_SERVER_ID = UUID.randomUUID().toString();

  public boolean USE_DEFAULT_SDK = true;

  public String COMPILE_SERVER_MAXIMUM_HEAP_SIZE = Integer.toString(ScalaCompileServerDefaults.DefaultHeapSize());

  /**
   * These are meant to be reasonable defaults. Our current default heap size is 2 Gb.
   * <p>
   * <ol>
   *     <li>
   *         {@code -Xss2m} sets the JVM thread stack size to 2 Megabytes. The Scala compiler can reach deep stack
   *         traces. In fact, some users reported this exact issue in the past in
   *         <a href="https://youtrack.jetbrains.com/issue/SCL-18766">SCL-18766</a>.
   *     </li>
   *     <li>
   *         {@code -XX:ReservedCodeCacheSize=384m} sets the JIT-compiled native code cache to 384 Megabytes (up from
   *         the default of 240 Megabytes). 384 is chosen as 50% higher than 256 (384 = 256 + 128). We're increasing the
   *         default value by about 50% because the cached Scala compilers benefit from a larger native code cache
   *         as larger chunks of the compiler can remain fully optimised. 512 Megabytes would be a bit too much when
   *         compared to the default heap size of 2 Gb (1/4 of the size). The reserved code cache sits outside the heap,
   *         so it contributes to the total memory usage of the Scala Compile Server, i.e. 384 Megabytes on top of the
   *         2-gigabyte default heap. For users who need it, they may tweak the heap size and the reserved code cache size
   *         as it suits their projects and their machines.
   *     </li>
   *     <li>
   *         {@code -XX:MaxInlineLevel=20} instructs the JIT compiler to increase the maximum depth of inlined method
   *         call chains. The default value is 9. The maximum value is 20. More aggressive inlining helps with
   *         abstractions and lambdas, which are very common in Scala code.
   *     </li>
   * </ol>
   */
  public String COMPILE_SERVER_JVM_PARAMETERS = "-Xss2m -XX:ReservedCodeCacheSize=384m -XX:MaxInlineLevel=20";
  public int COMPILE_SERVER_PARALLELISM = defaultMaxThreads();
  public boolean COMPILE_SERVER_PARALLEL_COMPILATION = true;

  //in minutes
  @ReportValue
  public int COMPILE_SERVER_SHUTDOWN_DELAY = 120;
  public boolean COMPILE_SERVER_SHUTDOWN_IDLE = true;

  public boolean USE_PROJECT_HOME_AS_WORKING_DIR = false;

  @TestOnly
  transient public String CUSTOM_WORKING_DIR_FOR_TESTS = null;

  @Override
  public ScalaCompileServerSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull ScalaCompileServerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static ScalaCompileServerSettings getInstance() {
    return ApplicationManager.getApplication().getService(ScalaCompileServerSettings.class);
  }

  /**
   * Same as {@link IncProjectBuilder#MAX_BUILDER_THREADS}.
   */
  @SuppressWarnings("UnstableApiUsage")
  private static int defaultMaxThreads() {
    int maxThreads = Math.min(10, (75 * Runtime.getRuntime().availableProcessors()) / 100); // 75% of available logical cores, but not more than 10 threads
    try {
      maxThreads = Math.max(1, Integer.parseInt(System.getProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, Integer.toString(maxThreads))));
    }
    catch (NumberFormatException ignored) {
      maxThreads = Math.max(1, maxThreads);
    }
    return maxThreads;
  }
}
