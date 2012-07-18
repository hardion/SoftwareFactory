package fr.soleil.software.ica

import com.google.common.io.Files

import fr.soleil.software.scm.model.ScmBuilder
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.maven.model.MavenProject
import fr.soleil.software.maven.model.MavenBOM
import fr.soleil.software.maven.model.MavenPackaging

import org.slf4j.LoggerFactory
import org.slf4j.Logger
  

class IcaConfiguration{

  static Logger log = LoggerFactory.getLogger(ScmBuilder.class)
  
  // categorize the project (useful to choose the Jenkins template, BOM and Distribution
  // def entry = datum.$packaging.$groupId.$os
  // entry.jenkins -> give the jenkins template to use for create the job
  // entry.bom -> give the name of the bom to update with the new project
  // entry.distribution -> give the name of the distribution to update with the new project
  static def datum=[ 
    nar:[
    ".*.lib":[
        any:[jenkins:"template.cpp",bom:"C-CPP-Libraries"],
        Linux:[jenkins:"template.cpp",bom:"C-CPP-Libraries"],
        Windows:[jenkins:"template.cpp",bom:"C-CPP-Libraries"],
        superpom:"super-pom-C-CPP-library",
        suffix:'-${aol}-${mode}-${library}',
        view:'CPP Libraries'
      ],
    "org.cdma.*":[
        any:[jenkins:"template.cpp",bom:"C-CPP-Libraries"],
        Linux:[jenkins:"template.cpp",bom:"C-CPP-Libraries"],
        Windows:[jenkins:"template.cpp",bom:"C-CPP-Libraries"],
        superpom:"NO SUPER POM",
        suffix:'-${aol}-${mode}-${library}',
        view:'CPP Libraries'
      ],
    ".*.device":[
        any:[jenkins:"template.cpp",bom:"C-CPP-Devices", distribution:["DeviceRoot-CPP-RHEL4","DeviceRoot-CPP-WIN32"]],
        Linux:[jenkins:"template.cpp",bom:"C-CPP-Devices", distribution:["DeviceRoot-CPP-RHEL4"]],
        Windows:[jenkins:"template.cpp",bom:"C-CPP-Devices", distribution:["DeviceRoot-CPP-WIN32"]],
        superpom:"super-pom-C-CPP-device",
        suffix:'-${aol}-${mode}',
        view:'CPP Devices'
      ]
    ],
    jar:[
    "fr.soleil.lib":[
        any:[jenkins:"template.java",bom:"Java-Applications",distribution:[/*"LiveRoot"*/]],
        superpom:"super-pom-java",
        view:'Java Libraries'
      ],
    "(fr.soleil.deviceservers)|(org.tango.server)":[
        any:[jenkins:"template.java",bom:"Java-Devices",distribution:["DeviceRoot-Java"]],
        superpom:"super-pom-java",
        view:'Java Devices'
      ],
    "fr.soleil.gui":[
        any:[jenkins:"template.java",bom:"Java-Applications",distribution:[/*"LiveRoot"*/]],
        superpom:"super-pom-java",
        view:'Java Applications'
      ],
    ".*":[
        any:[jenkins:"template.java",bom:"Java-Applications",distribution:[/*"LiveRoot"*/]],
        superpom:"super-pom-java",
        view:'Java Applications'
      ]
    ],
    war:[
    ".*":[
        any:[jenkins:"template.java",bom:"Java-Applications",distribution:[]],
        superpom:"super-pom-java",
        view:'Java Applications'
      ]
    ],
    pom:[
    "fr.soleil.gui":[
        any:[jenkins:"template.java",bom:"Java-Applications",distribution:[/*"LiveRoot"*/]],
        superpom:"super-pom-java",
        view:'Java Applications'
      ]
    ]
  ]

  static def mavenTemplate = { superpom, packaging, groupId, artifactId, simpleArtifactId, scm, build, developer ->
"""<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>fr.soleil</groupId>
    <artifactId>${superpom}</artifactId>
    <version>RELEASE</version>
  </parent>

  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <packaging>${packaging}</packaging>

  <name>${simpleArtifactId}</name>

  <scm>
    <connection>${scm.userURL}</connection>
    <developerConnection>${scm.devURL}</developerConnection>
    <url>${scm.viewURL}</url>
  </scm>

  ${build}
  <developers>
      <developer>
          <id>${developer}</id>
          <name>${developer}</name>
          <url>http://controle/</url>
          <organization>Synchrotron Soleil</organization>
          <organizationUrl>http://www.synchrotron-soleil.fr</organizationUrl>
          <roles>
              <role>manager</role>
          </roles>
          <timezone>1</timezone>
      </developer>
  </developers>
</project>
"""
  }
  
  static def narbuild=
  """
  <build>
    <plugins>
      <plugin>
        <groupId>org.freehep</groupId>
        <artifactId>freehep-nar-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
  """

  public static def createNewPOM(def groupId, def simpleArtifactId, def packaging, def platform, SCM scm, def developer){
    File tmp = Files.createTempDir()
    tmp.deleteOnExit()

    def project = new File(tmp, "pom.xml")
    
    def superpom = datum[packaging][groupId].superpom
    def artifactId = simpleArtifactId + (datum[packaging][groupId].suffix ?:"")
    
    def build = (packaging == "nar" ? narbuild : "")
    
    return mavenTemplate(superpom, packaging, groupId, artifactId, simpleArtifactId, scm, build, developer)
  }
  
  static def getDataFor(MavenProject project){
    def result
    if(datum[project.packaging]){
      def datumGroupId = datum[project.packaging].keySet().find{ project.groupId ==~/$it/ }
      if(datumGroupId){
        def data = datum[project.packaging][datumGroupId][project.os]
        if(data){
          result = data
          log.debug("Ask for data for $project : $data")

        }else{
          log.error("Os not supported for the given groupId and packaging : $project.os -> $project.groupId -> $project.packaging")
        }
      }else{
        log.error("GroupId not supported for the given packaging : $project.groupId -> $project.packaging")
      }
    }else{
      log.error("Packaging not supported : $project.packaging")
    }
    return result
  }
  
  static def getMavenBom(def name){
    def bomScm = ScmBuilder.create(":ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS", "ContinuousIntegration/maven/bom/"+name)
    new MavenBOM(bomScm)
  }
  static def getMavenPackaging(def name){
    def packScm = ScmBuilder.create(":ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS", "ContinuousIntegration/maven/packaging/"+name)

    new MavenPackaging(packScm)
  }
  
}
