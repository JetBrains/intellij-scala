package org.jetbrains.plugins.scala.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.info.ScalaFilesStorage;

import java.util.Collection;

import com.intellij.psi.PsiClass;

/**
 * @author Ilya.Sergey
 */
public interface ScalaFilesCache {

  public void init (boolean  b);

  public void setCacheUrls(@NotNull String[] myCacheUrls);

  public void setCacheName(@NotNull String myCacheName);

  public Collection<ScalaFileInfo> getAllClasses();

  public void setCacheFilePath(@NotNull final String dataFileUrl);

  public void saveCacheToDisk(final boolean runProcessWithProgressSynchronously);

  public PsiClass getClassByName(@NotNull final String name);

  public PsiClass[] getClassesByName(@NotNull final String name);

  public Collection<String> getAllClassNames();

  public Collection<String> getAllClassShortNames();

}
