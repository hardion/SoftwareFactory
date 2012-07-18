package fr.soleil.software.jenkins.model

import org.jvnet.hudson.test.*
import hudson.model.labels.LabelAtom
import hudson.matrix.AxisList
import hudson.matrix.TextAxis

import fr.soleil.software.jenkins.model.JenkinsREST
import fr.soleil.software.scm.model.ScmBuilder
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.maven.model.MavenProject
import fr.soleil.software.ica.IcaProxy

import groovy.xml.StreamingMarkupBuilder

import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.URLENC

import org.slf4j.*

// Get Job from Jenkins server
class JenkinsRestTest extends GroovyHudsonTestCase {
  def client
  
 
  void testCopyView(){
    this.prepareCopyView()
    // Prepare client side
    // 
    def client = new JenkinsREST(super.URL)

    // Stimulus
    assertUpdateItem(client)
    client.copyView("View1", "View2", "projectTemplate", "-RELEASE", false)
    client.createReportJobFromView("View1", "projectTemplate", "_REPORT")
    
    // Assert
    
    this.assertCopyView("-RELEASE")
  }

  private def prepareCopyView(){
    // Prepare server side
    // Syntax is like Groovy System Script -> Best way via REST API
    jenkins.setCrumbIssuer(null)
    
    // Create jobs
    def projectOne = super.createFreeStyleProject("projectOne");
    projectOne.scm = this.createCvsConf()
    
    def projectTwo = super.createMatrixProject("projectTwo");
    projectTwo.scm = this.createCvsConf()
    def axes = new AxisList()
    axes.add(new TextAxis("mode",["release","debug"]))
    projectTwo.axes = axes
    
    def projectTemplate = super.createFreeStyleProject("projectTemplate");
    projectTemplate.assignedLabel = new LabelAtom("FooLabel")
    
    // Create views
    def view1 = new hudson.model.ListView("View1")
    view1.jobNames.add(projectOne.name)
    view1.jobNames.add(projectTwo.name)

    def view2 = new hudson.model.ListView("View2")
    
//    def viewReport = new hudson.model.ListView("View_REPORT")
//    viewReport.

    jenkins.addView(view1)
    jenkins.addView(view2)
//    jenkins.addView(viewReport)

  }
  
  void assertUpdateItem(def ci){
    def expectedName = "TEST-NAME"
    def expectedDisplayName = "TEST-DISPLAYNAME"
    
    def item = ci.getItem("projectOne")
    item.name = expectedName
    item.displayName = expectedDisplayName
    ci.updateItem("Archiving.AdbArchivingServers", item)
    
    item = ci.getItem("projectOne")
    assertEquals(item.name,expectedName)
    assertEquals(item.displayName,expectedDisplayName)
    
  }
  
  private def assertCopyView(def suffix){
    // Prepare server side
    // Syntax is like Groovy System Script -> Best way via REST AI
    def view2 = jenkins.getView("View2")
    assertNotNull(jenkins.getJob("projectOne"+suffix))
    assertNotNull(jenkins.getJob("projectTwo"+suffix))

    // This stuff below can't work because we can't modify view from the remote API
//    assertEquals( 2, view2.items.size())
    
//    def view1 = jenkins.getView("View1")
//    assertEquals( view2.items[0].name, view1.items[0].name+suffix)
//    assertEquals( view2.items[1].name, view1.items[0].name+suffix)
    
  }
  
  private def assertReportView(){
    // Prepare server side
    // Syntax is like Groovy System Script -> Best way via REST AI
    def REPORT_SUFFIX = "_REPORT"
    def view2 = jenkins.getView("View"+REPORT_SUFFIX)
    assertNotNull(jenkins.getJob("projectOne"+REPORT_SUFFIX))
    
    def matrixReportJob = jenkins.getJob("projectTwo"+REPORT_SUFFIX)
    assertNotNull(matrixReportJob)

    // This stuff below can't work because we can't modify view from the remote API
//    assertEquals( 2, view2.items.size())
    
//    def view1 = jenkins.getView("View1")
//    assertEquals( view2.items[0].name, view1.items[0].name+suffix)
//    assertEquals( view2.items[1].name, view1.items[0].name+suffix)
    
  }

  private def createCvsConf(){
    new hudson.scm.CVSSCM(":pserver:toto@cvs-server:/usr/local/cvs", "allModules","","",true, true, true, false,"" )
  }
}