package org.jetbrains.plugins.scala.compiler;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.nio.charset.Charset;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.09.2008
 */

@State(
  name = "ScalacSettings",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/scala_compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class ScalacSettings implements PersistentStateComponent<ScalacSettings>, ProjectComponent {
  public String ADDITIONAL_OPTIONS_STRING = "";
  public int MAXIMUM_HEAP_SIZE = 128;
  public boolean GENERATE_NO_WARNINGS = false;
  private boolean DEPRECATION = false;

  public ScalacSettings getState() {
    return this;
  }

  public void loadState(ScalacSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ScalacSettings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static ScalacSettings getInstance(Project project) {
    return project.getComponent(ScalacSettings.class);
  }

  public String getOptionsString() {
    StringBuilder options = new StringBuilder();
    if(DEPRECATION) {
      options.append("-deprecation ");
    }
    if(GENERATE_NO_WARNINGS) {
      options.append("-nowarn ");
    }
    boolean isEncodingSet = false;
    final StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if("-deprecation".equals(token)) {
        continue;
      }
      if("-nowarn".equals(token)) {
        continue;
      }
      options.append(token);
      options.append(" ");
      if ("-encoding".equals(token)) {
        isEncodingSet = true;
      }
    }
    if (!isEncodingSet) {
      final Charset ideCharset = EncodingManager.getInstance().getDefaultCharset();
      if (!Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), ideCharset)) {
        options.append("-encoding ");
        options.append(ideCharset.name());
      }
    }
    return options.toString();
  }
}
