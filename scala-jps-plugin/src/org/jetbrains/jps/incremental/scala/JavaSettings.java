package org.jetbrains.jps.incremental.scala;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;

/**
 * @author Pavel Fatin
 */
public class JavaSettings {
  private File myHome;
  private File myExecutable;

  public JavaSettings(File home, File executable) {
    myHome = home;
    myExecutable = executable;
  }

  public File getHome() {
    return myHome;
  }

  public File getExecutable() {
    return myExecutable;
  }

  public static JavaSettings create(ModuleChunk chunk) {
    JpsModule module = chunk.representativeTarget().getModule();

    JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);

    if (sdk == null)
      throw new ConfigurationException("No JDK in module: " + module.getName());

    File home = new File(sdk.getHomePath());

    File executable = new File(JpsJavaSdkType.getJavaExecutable(sdk));

    return new JavaSettings(home, executable);
  }
}
