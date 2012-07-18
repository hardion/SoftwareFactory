/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.soleil.software.model

import fr.soleil.software.jenkins.model.JenkinsREST
import fr.soleil.software.sonar.model.SonarServer
import fr.soleil.software.maven.model.MavenProject
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.scm.model.ScmBuilder

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Proof
 * @author hardion
 */
class SoftwareDirectory {
  Logger log = LoggerFactory.getLogger(SoftwareDirectory.class)

	def softwares = [:]
  
  final JenkinsREST jenkins
  final SonarServer sonar

  static final def supportedJobPattern = /(.*)_(NAR|JAR)/
  
  SoftwareDirectory(JenkinsREST jenkins, SonarServer sonar ){
    this.jenkins = jenkins
    this.sonar = sonar

    def sonarKeys =[:]
    this.sonar.keys.collectEntries(sonarKeys){ 
      [(MavenProject.toSimpleArtifactId(it.replaceAll(":","."))):it]
    }
    log.debug("sonarKeys built :$sonarKeys")
    
    jenkins.jobNames.findAll{ it ==~ supportedJobPattern }.each{ jobname ->
      log.debug("Register Jobname in the SoftwareDirectory: $jobname")
      def key = (jobname =~ supportedJobPattern)[0][1]
      this.add(key,null, jobname, sonarKeys[key])        
    }
  }
  
  static def extractKeyFromJobName(String jobname){
    (jobname =~ supportedJobPattern)[0][1]
  }

  
  def getAt(String projectKey){
    softwares[projectKey]
  }
  
  def findFromJobKey(String jobKey){
    softwares.findResult{key, soft -> 
      soft.jobKey == jobKey ? soft : null
    }
  }
  
  def putAt(String projectKey, Software software){
    if(software.projectKey == projectKey){
      softwares[projectKey] = software   
    }
  }
  
  def add(def projectKey, def scm){
    this.addInner(new Software(projectKey, scm))
  }

  def add(def projectKey, def scmKey, def jobKey, def qualityKey=null){
    this.addInner(new SoftwareDirectory.Software(projectKey, scmKey, jobKey, qualityKey))
  }
  
  private def addInner(Software software){
    softwares[software.projectKey] = software
    return software
  }
  
  

  

  private class Software {
	
    final String projectKey
    final String jobKey
    String qualityKey
    String scmKey
    
    MavenProject project
    SCM scm
    def job
  
    Software(String projectKey, SCM scm){
      this.projectKey = projectKey
      this.scm = scm
      // Guess the job name
      def p = this.getProject() // Build Project
      this.jobKey = "${p.groupId}.${p.simpleArtifactId}_${p.packaging.toUpperCase()}"
    }
    
    Software(String projectKey, String scmKey){
      this.projectKey = projectKey
      this.scmKey = scmKey
      // Guess the job name
      def p = this.getProject() // Build Project
      this.jobKey = "${p.groupId}.${p.simpleArtifactId}_${p.packaging.toUpperCase()}"
    }
  
    Software(def projectKey, def scmKey, def jobKey, def qualityKey=null){
      this.projectKey = projectKey
      this.scmKey = scmKey
      this.jobKey = jobKey
      this.qualityKey = qualityKey
    }

    def getScm(){
      if(!scm){
        if(this.scmKey){
          scm = ScmBuilder.create(scmKey)
        }else if(this.getJob()){
          scm = jenkins.getScmConfiguration(this.job)         
          if(scm){
            scmKey = scm.scmKey
          }
        }
      }
      return scm 
    }
    
    def getScmKey(){
      if(!this.scmKey){
        this.getScm() // Initialize Scm and the key 
      }
      return this.scmKey 
    }
    
    def getProject(){
      if(!project){
        if(this.getScm()){
          project= new MavenProject(this.scm)         
        }
      }
      return project     
    }
    
    def getJob(){
      if(!job){
        if(jenkins.existJob(this.jobKey)){
          job = jenkins.getJob(this.jobKey)          
        }
      }
      return job
    }
    

    
    String toString(){
      "projectKey=$projectKey, scmKey=$scmKey, jobKey=$jobKey, qualityKey=$qualityKey"
    }

  }
  
}

