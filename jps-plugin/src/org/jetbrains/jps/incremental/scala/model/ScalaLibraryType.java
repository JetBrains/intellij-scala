package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.SettingsManager;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.library.JpsLibraryType;

/**
 * @author Pavel Fatin
 */
public class ScalaLibraryType implements JpsLibraryType<LibrarySettings> {
  private static final ScalaLibraryType myInstance = new ScalaLibraryType();

  @NotNull
  @Override
  public JpsElementChildRole<LibrarySettings> getPropertiesRole() {
    return SettingsManager.LIBRARY_SETTINGS_ROLE;
  }

  public static ScalaLibraryType getInstance() {
    return myInstance;
  }
}
