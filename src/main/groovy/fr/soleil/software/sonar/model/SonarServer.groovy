package fr.soleil.software.sonar.model

import org.sonar.wsclient.Sonar;
import org.sonar.wsclient.services.ResourceQuery;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.PropertyQuery;
import org.sonar.wsclient.services.Property;
import org.sonar.wsclient.services.PropertyUpdateQuery;
import org.sonar.wsclient.services.ProjectDeleteQuery;
import org.sonar.wsclient.services.ViolationQuery;
import org.sonar.wsclient.services.Violation;

import groovy.xml.StreamingMarkupBuilder

import org.xml.sax.ext.LexicalHandler // for CDATA
import org.xml.sax.SAXException;

import fr.soleil.software.maven.model.MavenProject;

class SonarServer{
  
  // Create WebService client
  Sonar sonar = Sonar.create("http://calypso/sonar","ica", "impega");

  static String[] DEFAULT_METRICS=["blocker_violations","major_violations","critical_violations"]
  static def DEFAULT_LANGUAGE="any"
  static def JAVA_LANGUAGE="java"
  static def CPP_LANGUAGE="c++"

  /**
   */
  def findCppProjects(){
    // Retrieve views property
    ResourceQuery rq = ResourceQuery.create(null)
    List<Resource> resources = sonar.findAll(rq).findAll(){ it.language=="c++"}
  }

  /**
   */
  List<String> getKeys(){
    // Retrieve views property
    ResourceQuery rq = ResourceQuery.create(null)
    sonar.findAll(rq).collectAll(){ it.key}
  }
  
  boolean isValidKey(String key){
    ResourceQuery rq = ResourceQuery.create(key)
    sonar.find(rq)!=null
  }

  def findAllProjectByMetric(def language=DEFAULT_LANGUAGE, def metrics=DEFAULT_METRICS){
    ResourceQuery rq = ResourceQuery.create(null)
    rq.setMetrics(metrics)
    
    List<Resource> resources = sonar.findAll(rq)
    if(language != DEFAULT_LANGUAGE){
      resources = resources.findAll(){ it.language==language}
    }
    return resources
  }
  
  /**
   * Update the MASTER_VIEW
   */
  def findWorstCppProjects(){
    // Retrieve views property
    Resource res = new Resource();
    res.setLanguage("c++")
    
    ResourceQuery rq = ResourceQuery.create(null)
//    rq.setRuleSeverities("BLOCKER","CRITICAL","MAJOR")
    rq.setMetrics("blocker_violations","major_violations","critical_violations")
    List<Resource> resources = sonar.findAll(rq).findAll(){ it.language=="c++"}
//    ViolationQuery vq = ViolationQuery.createForResource(res)
//    vq.setPriorities(["MAJOR","CRITICAL","BLOCKER"])
//    
//    List<Violation> violations = sonar.findAll(vq)
//    return violations;
  }
  /**
   * Update the MASTER_VIEW
   */
  def updateViewsPlugin(List<MavenProject> distribs, def exceptions){
    // Retrieve views property
    Property properties = sonar.find(PropertyQuery.createForKey("views.def"))
    println properties.value

    // Change de views configuration from all maven project defined by the dependencies of packages
    distribs.each() { distrib ->

      def projects =[]
      distrib.allDependencies.collect(projects){groupId, artifactId -> 
        def key = "$groupId:$artifactId"
        exceptions[key]?:key
      }

      def artifactId = distrib.project.artifactId.text()
      def desc = distrib.project.name.text()+" : "+distrib.project.description.text()
      if(projects){
        println "Create view for $artifactId"
        // Sub View of MASTER project
        properties.value = addSonarView(properties.value, 'MASTER_PROJECT', artifactId, artifactId, desc, projects)
        // Independant view 
        //properties.value = addSonarView(properties.value, "", "${artifactId.toUpperCase()}", artifactId, desc, projects)
      }else{
        println "[WARNING] $name has no dependendencies ?!!"
      }
    }
    // Finally update the views
    //properties.value = new File("src/main/groovy/fr/soleil/sonar/base.xml").text.replace('\n','').trim()
    sonar.update(new PropertyUpdateQuery(properties))
  }
  
  /**
   * update Independant View From Master SubView
   */
  def updateIndependantView(String xml){

    // Object model from xml
    def views = new XmlSlurper().parseText(xml)
    
    views.depthFirst().grep{ it.@parent == "MASTER_PROJECT" }.each(){ view ->
      
      def projects = []
      view.p.collect(projects){ it.text() } 
      
      def name = view.name.text()
      def regexp = view.regexp ? view.regexp.text(): ""
      def desc = view.desc ? view.desc.text(): ""
      
      println "TRYing to SynC :  ${view.@key.text()}-VIEW, ${name}"
      xml = addSonarView(xml, "", "${view.@key.text()}-VIEW", name, desc, projects, regexp)
    }
    return xml
  }
  

  def addSonarView(def xml, def parent, def newkey, def newname, def newdesc, def projects,def regex=""){
    // Object model from xml
    def views = new CDataXmlSlurper().parseText(xml)
    
    // Delete the view if exists
    views.vw.find{ view -> view."@key"=="$newkey" }?.replaceNode {}
    
    def common = {
      name {mkp.yieldUnescaped("<![CDATA[$newname]]>")}
      if(newdesc){
        desc {mkp.yieldUnescaped("<![CDATA[$newdesc]]>")}
      }
      if(regex){
        regexp {mkp.yieldUnescaped("<![CDATA[$regex]]>")}
      }
      projects.each(){
        p("$it")
      }
    }
      
    
    // Add the new view  
    def fview = views.appendNode{
      if(parent){
        vw (key:newkey, parent:parent, root:parent, "def":'false', common)
      }else{
        vw (key:newkey, common, "def":'false' )
      }
    }
  
    //Serialize in xml stream
    new StreamingMarkupBuilder().bind{ mkp.yield views }
  
  }
  
  def deleteProject(def key){
    sonar.delete(ProjectDeleteQuery.create(key))
  }
  
}
// STUFF
// Allow slurping xml with CDATA
class CDataXmlSlurper extends XmlSlurper implements LexicalHandler {
  protected final static char[] sCDATA= '<![CDATA[';
  protected final static char[] eCDATA= ']]>';
  boolean keepCData=true;
  private boolean isCData=false;
  private final StringBuffer charBuffer = new StringBuffer();
  // This is not a patch (just keep the compatibility)
  private boolean keepWhitespace = false;
  
  CDataXmlSlurper(){
    super();
    // This is a patch
    setProperty('http://xml.org/sax/properties/lexical-handler', this);
  }
  
  /* (non-Javadoc)
   * @see org.xml.sax.ContentHandler#characters(char[], int, int)
   */
  public void characters(final char[] ch, final int start, final int length) throws SAXException {
    // This is a patch
    if(keepCData && isCData){
      this.charBuffer.append(sCDATA,0,sCDATA.length);
      this.charBuffer.append(ch,start,length);
      this.charBuffer.append(eCDATA,0,eCDATA.length);
      // Doesn't call super.characters(ch,start,length);
      // so the XmlSlurper won't add any child node
      // The call to endCDATA will add the good node
    }else{
      super.characters(ch,start,length);
    }
  }

  // This is a patch
  void comment(char[] ch, int start, int length) { }
   
  // This is a patch
  void endCDATA() {
    
    if (this.charBuffer.length() != 0) {
      //
      // This element is preceded by CDATA if keepWhitespace is false (the default setting) and
      // it's not whitespace add it to the body
      // Note that, according to the XML spec, we should preserve the CDATA if it's all whitespace
      // but for the sort of work I'm doing ignoring the whitespace is preferable
      //
      final String cdata = this.charBuffer.toString();
      
      this.charBuffer.setLength(0);
      if (this.keepWhitespace || cdata.trim().length() != 0) {
        super.document.getAt(0).addChild(new CDataBuildable(cdata));
      }
    }
    isCData=false;
  }
   
  // This is a patch
  void endDTD() {}
   
  // This is a patch
  void endEntity(String name) {}
   
  // This is a patch
  void startCDATA() {isCData=true;}
   
  // This is a patch
  void startDTD(String name, String publicId, String systemId) {}
   
  // This is a patch
  void startEntity(String name) {} 
  
  /**
   * @param keepWhitespace
   * 
   * If true then whitespace before elements is kept.
   * The default is to discard the whitespace.
   */
  // This is not a patch (just keep compatibility)
  public void setKeepWhitespace(boolean keepWhitespace) {
    this.keepWhitespace = keepWhitespace;
    super.setKeepWhitespace(keepWhitespace);
  }

}

class CDataBuildable implements Buildable {
  final String cdata
  public CDataBuildable(final String cdata){
    this.cdata=cdata;
  }
  
  public void build(GroovyObject builder){
    // when call from java context
    // builder.getProperty("mkp");
    // builder.invokeMethod("yieldUnescaped", new Object[]{cdata});

    builder.unescaped << this.cdata;
  }
  public String toString(){
    return cdata;
  }
}


