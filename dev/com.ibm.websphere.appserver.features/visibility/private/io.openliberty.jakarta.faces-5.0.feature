-include= ~${workspace}/cnf/resources/bnd/feature.props
symbolicName=io.openliberty.jakarta.faces-5.0
singleton=true
-features=com.ibm.websphere.appserver.eeCompatible-11.0, \
  io.openliberty.noShip-1.0
-bundles=io.openliberty.jakarta.faces.4.0; location:="dev/api/spec/,lib/"; mavenCoordinates="org.apache.myfaces.core:myfaces-api:4.0.0"
kind=noship
edition=full
WLP-Activation-Type: parallel
