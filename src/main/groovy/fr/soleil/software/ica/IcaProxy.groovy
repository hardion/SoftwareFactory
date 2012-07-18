package fr.soleil.software.ica

class IcaProxy {
  
  static configureProxy(){
    System.setProperty("http.proxyHost","195.221.0.6")
    System.setProperty("http.proxyPort","8080")
    System.setProperty("https.proxyHost","195.221.0.6")
    System.setProperty("https.proxyPort","8080")
    System.setProperty("http.nonProxyHosts","*.ica|*.synchrotron-soleil.fr|calypso|ganymede|controle")
  }
}