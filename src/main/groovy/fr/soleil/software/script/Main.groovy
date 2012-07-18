package fr.soleil.software.script

import java.util.zip.ZipInputStream
import com.google.common.io.Files

import fr.soleil.software.model.SoftwareFactory
import fr.soleil.software.model.DeleteType


//args=[/*function = */ "importProjectByScm",/*scmroot =*/ ":pserver:anonymous:@ganymede.synchrotron-soleil.fr:/usr/local/CVS", /*scmmodule = */"Emphu"]

// Configuration
bootstrap()
callFunction=true

sf = new SoftwareFactory()
functions = [
  deleteProject : [
    parameters : [
      groupId : "GroupId of the project to delete",
      artifactId : "ArtifactId of the project to delete",
      message : "Describe here why you close the project",
      obsolete : "True if you consider to close the project because it's obsolete"],
    closure : { 
      def type = (obsolete=="true" ? DeleteType.CLOSE : DeleteType.MOVE)
      sf.deleteProject(groupId, artifactId, type, message)
    }
  ],
  releaseProjectByJob : [
    parameters : [
      jobname : "Name of the jenkins job"],
    closure : { sf.releaseByJob(jobname)  }
  ],
  releaseProjectByScm : [
    parameters : [
      scmroot : "SCM place of the project to release : the root part",
      scmmodule : "SCM path of the project to release : the module part"],
    closure : { sf.releaseByScm(scmroot, scmmodule)  }
  ],
  importProjectByScm : [
    parameters : [
      scmroot : "SCM place of the project to release : the root part",
      scmmodule : "SCM path of the project to release : the module part"],
    closure : {   sf.importProjectByScm(scmroot, scmmodule) }
  ],
  projectInfo : [
    parameters : [ views : "Views from where will be found out the information"],
    closure : {   
      sf.jenkins.printProjectInfo(views).each { info ->
        info.each{ 
          print it+"\t"
        }
        println ""
      }
    }
  ],

  addProject : [
    parameters : [
      groupId : "GroupId of the new project",
      artifactId : "Simple ArtifactId of the new project",
      packaging : "Packaging of the new project (can be jar, nar ...)",
      platform : "Platform of the new project (can be any, linux, windows ...)",
      scmroot : "Future SCM place of the new project : the root part",
      scmmodule : "Future SCM path of the new project : the module part",
      developer : "Main developer",
      zip : "Zip File where to find the files to import",
      message : "Describe here why you add the project"],
    closure : { 
      File dir 
      if(zip && (zipFile = zip as File).exists() ){
        dir = Files.createTempDir()
        dir.deleteOnExit()
        zipFile.unzip(dir.absolutePath)
      }
      sf.addProject(groupId, artifactId, packaging, platform, scmroot, scmmodule, developer, dir, message )  
    }
  ],
  moveProject : [
    parameters : [
      groupId : "GroupId of the new project",
      artifactId : "Simple ArtifactId of the new project",
      scmroot : "Future SCM place of the new project : the root part",
      scmmodule : "Future SCM path of the new project : the module part",
      message : "Describe here why you move the project"],
    closure : { sf.moveProject(groupId, artifactId, scmroot, scmmodule, message)  }
  ],
  createReleaseJob : [
    parameters : [
      originalView : "The base view to create release job",
      templateJob : "The base job for any new release job",
      dependencyViews : "The further views where to find the maven dependency",
      forceDelete : "Allow to force the recreation of release job from the template "
    ],
    closure : { 
      sf.jenkins.createReleaseJobFromView(originalView, templateJob, forceDelete=="true")
      def views = (dependencyViews=="NONE" ? [] : dependencyViews.tokenize(",") )
      sf.jenkins.updateJobDependencies("${originalView}_RELEASE", views)
    }
  ],
  createReportJob : [
    parameters : [
      originalView : "The base view to create report job",
      templateJob : "The base job for any new report job",
      forceDelete : "Allow to force the recreation of report job from the template "
    ],
    closure : { 
      sf.jenkins.createReportJobFromView(originalView, templateJob, forceDelete=="true")
    }
  ],
  impactReport : [
    parameters : [
      job : "The root job from where to find all jobs to scan among its dependencies",
      pattern : "The pattern to find in console output of the last build"
    ],
    closure : {
      sf.jenkins.printlnImpact(job, pattern)
    }
  ],
  lastBuildReport : [
    parameters : [
      jobPattern : "The pattern to list all jobs to scan",
      logPattern : "The pattern to find in console output of the last build"
    ],
    closure : {
      def jobs = sf.jenkins.jobNames.findAll{ it.text() ==~ jobPattern}
      jobs.each { jobname -> 
        sf.jenkins.showLastLog(jobname.text(), logPattern)
      }
    }
  ]
  
]


// Get parameters from the interactive shell
reader = System.in.newReader()
// create new version of readLine that accepts a prompt to remove duplication from the loop
reader.metaClass.readLine = { String prompt -> println prompt ; readLine() }

// Manage and fill parameters
parameters()

println binding.variables

// Exec the function
if(callFunction){
  functions[binding.function].closure.call()
}

println "DONE"


def interactive(variable, description){
  binding."$variable" = reader.readLine(" Enter the value of $variable : ")
}

// Retrieve all parameters mandatory for the function
def parameters(){
  def i=0
    
  if(! binding.variables?.containsKey("function")){
    if (args) {
      binding.function = args[i++]
    }else{
      interactive("function", "Please enter the name of the function ")
    }
  }

  def params = functions[binding.function].parameters
  def checkargs = args.size() > params.size() //count also the first argument for the function's name

  params.each(){ key, description ->
    if( ! binding.variables?.containsKey(key) && checkargs) {
      binding."$key" = args[i++]
    }else{
      interactive(key, description)
    }
  }
}

def bootstrap(){
  
  // Code from http://grooveek.blogspot.com/2009/09/adding-zipping-and-unzipping.html
  File.metaClass.unzip = { String dest ->
    //in metaclass added methods, 'delegate' is the object on which
    //the method is called. Here it's the file to unzip
    def result = new ZipInputStream(new FileInputStream(delegate))
    def destFile = new File(dest)
    if(!destFile.exists()){
      destFile.mkdir();
    }
    result.withStream{
      def entry
      while(entry = result.nextEntry){
        if (!entry.isDirectory()){
          new File(dest + File.separator + entry.name).parentFile?.mkdirs()
          def output = new FileOutputStream(dest + File.separator
            + entry.name)                       
          output.withStream{
            int len = 0;
            byte[] buffer = new byte[4096]
            while ((len = result.read(buffer)) > 0){
              output.write(buffer, 0, len);
            }
          }
        }
        else {
          new File(dest + File.separator + entry.name).mkdir()
        }
      }
    }
  }

}