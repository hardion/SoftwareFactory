package fr.soleil.software.jenkins.script

import fr.soleil.software.jenkins.model.JenkinsREST
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.URLENC

def jenkins = new JenkinsREST()

def url = jenkins.query("")
def name="fr.soleil.lib.SFTest"
def templateName="template.java"

    def client = new RESTClient(url)
    def postBody = ["name":name,"mode":"copy","from":templateName] // will be url-encoded
    def resp = client.post(path:'createItem', body:postBody, requestContentType : URLENC  )
    if(resp.status == 200){
      println ("Response : Create new Item ${resp.data}")
    }else{
      println ("Response error : Create new Item ${resp.data}")
    }

