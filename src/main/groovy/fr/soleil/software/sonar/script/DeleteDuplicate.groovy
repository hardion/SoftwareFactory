package fr.soleil.software.sonar.script

import fr.soleil.software.sonar.model.SonarServer;

def sonar = new SonarServer()
def map=[:]

// get C++ project in map [name:[key1, key2 ...]]
sonar.findCppProjects().each(){
 
  if(!map[it.name]){
    map[it.name]=[it.key]
  }else{
    map[it.name]+=it.key
  }
}

// filter only duplicate
def duplicate =[:]
map.each(){key, value ->
  if(value.size() > 1){
    duplicate[key]=value
  } 
}

// delete debug and undefault platform
duplicate.each(){ key, value ->
  // filter debug
  def deleted = []
  value.grep(~/.*debug/).each(){
    deleted+=it
  }
  def other = value-deleted
  
  // keep only one platform
  def kept = other.find(){ it ==~ /.*Linux.*/ }?:other.find(){ it ==~ /.*Windows.*/ }
  deleted += (other-kept)
  
  println "(kept=$kept)++++++(deleted=$deleted)=====(value=$value)"
  
  deleted.each(){
    sonar.deleteProject(it)
  }
}
