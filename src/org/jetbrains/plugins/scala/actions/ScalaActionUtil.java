package org.jetbrains.plugins.scala.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.facet.FacetManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.plugins.scala.config.ScalaFacetType;

/**
 * @author ilyas
 */
public class ScalaActionUtil {

  public static boolean isScalaConfigured(AnActionEvent e) {
    DataContext context = e.getDataContext();
    Module module = (Module) context.getData(DataKeys.MODULE.getName());
    if (module != null) {
      FacetManager manager = FacetManager.getInstance(module);
      return manager.getFacetByType(ScalaFacetType.INSTANCE.getId()) != null;
    }
    return false;
  }

}
