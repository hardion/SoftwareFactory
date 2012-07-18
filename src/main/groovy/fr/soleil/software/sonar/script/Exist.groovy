package fr.soleil.software.sonar.script

import fr.soleil.software.sonar.model.SonarServer;

def sonar = new SonarServer()

// get C++ project in map [name:[key1, key2 ...]]
println sonar.isValidKey( "fr.soleil.deviceservers:TangoParser" )