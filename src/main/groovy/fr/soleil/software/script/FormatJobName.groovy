package fr.soleil.software.script

import fr.soleil.software.model.SoftwareFactory
import fr.soleil.software.sonar.model.SonarServer
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.maven.model.MavenProject

import org.jggug.kobo.gexcelapi.GExcel

/**
 * CREATE A INFORMATION REPORT FROM THE SOFTWARE FACTORY
 * 
 */
sf = new SoftwareFactory()

jobs = sf.jenkins.getJobsView("CPP Devices")

def result="["
jobs.each() { 
        def jobname = it.name.text()
        def job = sf.jenkins.getJob(jobname)
        def project = sf.jenkins.from(job)
        if(project){
          def VERSION= project.scm.isTag()? "RELEASE" : "HEAD"
          def standardName= "${project.groupId}.${project.simpleArtifactId}_${project.packaging.toUpperCase()}"
          result+="[\"$jobname\",\"$standardName\"],"         
        }
}

println result[0..-2]+"]"