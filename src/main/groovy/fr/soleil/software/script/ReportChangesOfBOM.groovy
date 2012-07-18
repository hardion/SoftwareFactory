/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.soleil.HudsonManagement.process


println modules
println tags

m = modules.tokenize(',')
t = tags=="" ? [] : tags.tokenize(',')

if(t.size() <= m.size()){
    if(m.size() != 0){
        // Case : One tag for all modules
        if(t.size()==1 && m.size()!=1){
            (1..(m.size()-1)).each(){
                t.push(t[0])
            }
        }
        
        printReport(cvsroot, m, t)
        
    }else{
            println "No Module defined !!!"
    }
}else{
    println "More Tag than Module !!!"
}




/* 
 * COPY FROM UTILITIES
 * I don't take enough time to find how to use other groovy classe in hudson context (
 */
def getLastReleaseTag(def cvsroot, def module, def file){
    result = ""
    def proc = "cvs -d ${cvsroot} rlog -h ${module}/${file}".execute() // Create the String
    proc.waitFor()                               // Wait for the command to finish

    // Obtain status and output
    if( proc.exitValue()!= "1" ){
        version = proc.in.text =~ /BOM-(.*):/
        if(version.find()){
            result = "BOM-"+version[0][1]
        }
    }else{
      println "ERROR $proc.in.text"
    }
    return result
}

def pom(def cvsroot, def module){
    new XmlParser().parseText( "cvs -Q -d ${cvsroot} checkout -p ${module}/pom.xml".execute().text)
}

def pom(def cvsroot, def module, def tag){
    new XmlParser().parseText( "cvs -Q -d ${cvsroot} checkout -Pp -r ${tag} ${module}/pom.xml".execute().text)
}

def getVersionsPropertiesOfProfile(def project, def id){
    def result = [:]
    for( profile in project.profiles[0]){
        if(profile.id.text() == id){
            for( p in profile.properties[0]){
                name = p.name() instanceof groovy.xml.QName ? p.name().localPart : p.name()
                result.put(name, p.text())
            }
        }
    }
    return result
}

def getVersionsProperties(def project){
    def result = [:]
    for( p in project.properties[0]){
        name = p.name() instanceof groovy.xml.QName ? p.name().localPart : p.name()
        result.put(name, p.text())
    }

    return result
}

def void printCSVDiff(def olds, def heads){

    //Now Just compare the 2 resulted maps
    def common=olds.keySet().intersect(heads.keySet())
    def added=heads.keySet().toList()
    added.removeAll(common)
    def deleted=olds.keySet().toList()
    deleted.removeAll(common)

    def result=[]
    common.each(){
        value = heads[it]
        if("${value}" != "${olds[it].value}"){
            result.add(it)
        }
    }


    println "Artifact\tState\tOld Version\tNew Version"

    result.each(){
        println "${it}\tModified\t${olds[it]}\t${heads[it]}"
    }
    added.each(){
        println "${it}\tAdded\t---\t${heads[it]}"
    }
    deleted.each(){
        println "${it}\tDeleted\t${olds[it]}\t---"
    }

}


def void printReport(def cvsroot, def modules, def tags){

    println "cvsroot=${cvsroot}"
    println "modules=${modules}"
    println "tags=${tags} ${tags.size()}"

    def tag=""
    def i=0

    for( module in modules){

        // Check last tag of each module
        println "tags[i] = ${tags[i]}"
        if( tags.size() == 0 || tags[i]=="LAST_RELEASE_TAG"){
            tag = getLastReleaseTag(cvsroot,module,"pom.xml")
        }else{
            tag = tags[i++]
        }

        // Compare a tag (arg2) with
        //diff = "cvs -d ${args[0]} -z3 -f rdiff -uf -r ${arg[2]} ${arg[1]}".execute().text
        //cvsroot=args[0]
        //module= args[1]
        //tag=args[2]
        println "Difference for ${module} between ${tag} and the head"
        def project = pom(cvsroot, module, tag)
        def olds = [:]
        def heads = [:]

        //TODO : Remove specific code
        if(module=="ContinuousIntegration/maven/configuration/super-pom-soleil/BOM/C-CPP-3rdParties"){


            olds = getVersionsProperties(project)
            project = pom(cvsroot, module)
            heads = getVersionsProperties(project)

        }else{
            olds = getVersionsPropertiesOfProfile(project, "release")
            project = pom(cvsroot, module)
            heads = getVersionsPropertiesOfProfile(project, "release")
        }

        printCSVDiff(olds, heads)

    }
}
