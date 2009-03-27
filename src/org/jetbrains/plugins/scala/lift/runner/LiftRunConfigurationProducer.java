package org.jetbrains.plugins.scala.lift.runner;

import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.runner.*;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenArtifact;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.dom.model.Dependencies;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public class LiftRunConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {

  private PsiElement mySourceElement;
  private static final String GROUP_ID_LIFT = "net.liftweb";
  private static final String ARTIFACT_ID_LIDT = "lift-webkit";

  private static final String JETTY_RUN = "jetty:run";

  public LiftRunConfigurationProducer() {
    super(ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class));
  }

  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  private MavenRunnerParameters createBuildParameters(Location l) {
    final PsiElement element = l.getPsiElement();
    final Project project = l.getProject();

    final Module module = ModuleUtil.findModuleForPsiElement(element);
    if (module == null) return null;

    final MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
    final MavenProjectModel mavenProjectModel = mavenProjectsManager.findProject(module);

    final MavenArtifact artifact = mavenProjectModel.findDependency(GROUP_ID_LIFT, ARTIFACT_ID_LIDT);

    if (artifact == null) return null;

    mySourceElement = element;

    List<String> profiles = MavenProjectsManager.getInstance(project).getActiveProfiles();
    List<String> goals = new ArrayList<String>();

    goals.add(JETTY_RUN);

    return new MavenRunnerParameters(true, module.getModuleFile().getParent().getPath(), goals, profiles);
  }

  private static RunnerAndConfigurationSettingsImpl createRunnerAndConfigurationSettings(MavenGeneralSettings generalSettings,
                                                                                         MavenRunnerSettings runnerSettings,
                                                                                         MavenRunnerParameters params,
                                                                                         Project project) {
    MavenRunConfigurationType type = ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);
    final RunnerAndConfigurationSettingsImpl settings = RunManagerEx.getInstanceEx(project)
        .createConfiguration(MavenRunConfigurationType.generateName(project, params), type.getConfigurationFactories()[0]);
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration) settings.getConfiguration();
    runConfiguration.setRunnerParameters(params);
    if (generalSettings != null) runConfiguration.setGeneralSettings(generalSettings);
    if (runnerSettings != null) runConfiguration.setRunnerSettings(runnerSettings);
    return settings;
  }


  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Module module = context.getModule();
    if (module == null || !ScalaUtils.isSuitableModule(module)) return null;

    final MavenRunnerParameters params = createBuildParameters(location);
    if (params == null) return null;
    return createRunnerAndConfigurationSettings(null, null, params, location.getProject());

  }

  private static boolean isTestDirectory(final Module module, final PsiElement element) {
    final PsiDirectory dir = (PsiDirectory) element;
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    final ContentEntry[] entries = manager.getContentEntries();
    for (ContentEntry entry : entries) {
      for (SourceFolder folder : entry.getSourceFolders()) {
        if (folder.isTestSource() && folder.getFile() == dir.getVirtualFile()) {
          return true;
        }
      }
    }
    return false;
  }


  public int compareTo(final Object o) {
    return PREFERED;
  }


}
