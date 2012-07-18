package fr.soleil.software.sonar.script

import fr.soleil.software.sonar.model.SonarServer;

import fr.soleil.software.scm.model.ScmBuilder
import fr.soleil.software.maven.model.MavenProject
/**
 * Sonar Management by WebService
 * 
 */
def cvsroot=":pserver:anonymous@ganymede.synchrotron-soleil.fr:/usr/local/CVS"
def base="ContinuousIntegration/maven/packaging"
def exceptions = ["fr.soleil.gui.salsa:SalsaClient":"fr.soleil.gui.salsa:Salsa"]

// Checkout the maven project
def distribs = [
  //new MavenProject(ScmBuilder.create(cvsroot,"$base/DataReductionRoot")),
  //  new MavenProject(ScmBuilder.create(cvsroot,"$base/FrozenRoot")),
  //new MavenProject(ScmBuilder.create(cvsroot,"$base/LegacyRoot")),
  //new MavenProject(ScmBuilder.create(cvsroot,"$base/LiveRoot")),
  //  new MavenProject(ScmBuilder.create(cvsroot,"ContinuousIntegration/maven/projets/global-root-assembler")),
  //  new MavenProject(ScmBuilder.create(cvsroot,"ContinuousIntegration/maven/projets/passerelle/passerelle_core")),
  //new MavenProject(ScmBuilder.create(cvsroot,"$base/ArchivingRoot"))
  new MavenProject(ScmBuilder.create(cvsroot,"$base/DeviceRoot-Java"))
]

// Update the configuration of Sonar Views plugin
new SonarServer().updateViewsPlugin(distribs, exceptions)