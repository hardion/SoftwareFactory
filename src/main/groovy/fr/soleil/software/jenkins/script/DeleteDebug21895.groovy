package fr.soleil.software.jenkins.script

import fr.soleil.software.jenkins.model.JenkinsREST

import groovy.xml.StreamingMarkupBuilder

jenkins = new JenkinsREST()

["CPP Libraries", "CPP Devices", "ze_lr_CPP Libraries", "ze_lr_CPP Devices", "ze_report_CPP Libraries", "ze_report_CPP Devices", "ze_report_ze_lr_CPP Libraries", "ze_report_ze_lr_CPP Devices"].each{
  listView = jenkins.getJobsView(it)
  jobs =  listView.job
  jobs.each{ jobview ->
    jobname = jobview.name.text()
    job = jenkins.getJob(jobname)
    
    if(job.name() == "matrix-project"){
      // Remove the debug mode axis
      modeAxis = job.axes."hudson.matrix.TextAxis".find{ it.name.text() == "mode" }
      modeAxis.values.replaceBody(){
        string("release")
      }
      // Update the job in Jenkins
      jenkins.updateJob(jobname, job)
    
      println "Job $jobname won't more compile in debug mode"
    }else{
      println "Job $jobname : nothing to do"
    }
  }
}