package org.jetbrains.plugins.scala.lift.runner;

import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.idea.maven.project.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class LiftRunConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {

  private PsiElement mySourceElement;
  private static final String GROUP_ID_LIFT = "net.liftweb";
  private static final String ARTIFACT_ID_LIFT = "lift-webkit";

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
    final MavenProject mavenProject = mavenProjectsManager.findProject(module);

    if (mavenProject == null) return null;

    //todo: check this code
    final List<MavenArtifact> dependencies = mavenProject.getDependencies();
    MavenArtifact artifact = null;
    for (MavenArtifact dependence : dependencies) {
      if (dependence.getArtifactId().equals(GROUP_ID_LIFT)) {
        artifact = dependence;
        break;
      } else if (dependence.getArtifactId().equals(ARTIFACT_ID_LIFT)) {
        artifact = dependence;
        break;
      }
    }
    //final MavenArtifact artifact = mavenProjectModel.findDependency(GROUP_ID_LIFT, ARTIFACT_ID_LIFT);

    if (artifact == null) return null;

    mySourceElement = element;

    List<String> profiles = MavenProjectsManager.getInstance(project).getActiveProfiles();
    List<String> goals = new ArrayList<String>();

    goals.add(JETTY_RUN);

    final VirtualFile file = module.getModuleFile();
    if (file == null) return null;

    final VirtualFile parent = file.getParent();
    if (parent  == null) return null;
    
    return new MavenRunnerParameters(true, parent.getPath(), goals, profiles);
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
