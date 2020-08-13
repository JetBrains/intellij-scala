package org.jetbrains.plugins.scala.debugger.filters;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static java.util.Collections.singletonList;

/**
 * @author ilyas
 */
public class ScalaDebuggerSettings extends XDebuggerSettings<ScalaDebuggerSettings> implements Getter<ScalaDebuggerSettings> {

  public Boolean DEBUG_DISABLE_SPECIFIC_SCALA_METHODS = true;
  public Boolean FRIENDLY_COLLECTION_DISPLAY_ENABLED = true;
  public Boolean DONT_SHOW_RUNTIME_REFS = true;
  public Boolean DO_NOT_DISPLAY_STREAMS = true;
  public Integer COLLECTION_START_INDEX = 0;
  public Integer COLLECTION_END_INDEX = 49;
  public Boolean SHOW_VARIABLES_FROM_OUTER_SCOPES = true;
  public Boolean ALWAYS_SMART_STEP_INTO = true;

  public ScalaDebuggerSettings() {
    super("scala_debugger");
  }

  @NotNull
  @Override
  public Collection<? extends Configurable> createConfigurables(@NotNull DebuggerSettingsCategory category) {
    //todo: split settings configurables somehow
    switch (category) {
      case GENERAL:
        return singletonList(new ScalaDebuggerSettingsConfigurable(this));
      case STEPPING:
        return singletonList(SimpleConfigurable.create("scala.debugger.stepping" ,"Scala", ScalaSteppingConfigurable.class, this));

    }
    return Collections.emptyList();
  }

  public ScalaDebuggerSettings getState() {
    return this;
  }

  public void loadState(final ScalaDebuggerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static ScalaDebuggerSettings getInstance() {
    return getInstance(ScalaDebuggerSettings.class);
  }

  @Override
  public ScalaDebuggerSettings get() {
    return this;
  }
}