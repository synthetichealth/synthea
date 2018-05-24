#/bin/bash
JHOME=`/usr/libexec/java_home`
keytool -import -alias mitre_ba_root -file MITRE\ BA\ Root.crt -keystore "$JHOME/jre/lib/security/cacerts" -storepass changeit -noprompt
keytool -import -alias mitre_ba_npe_ca1 -file MITRE\ BA\ NPE\ CA-1.crt -keystore "$JHOME/jre/lib/security/cacerts" -storepass changeit -noprompt
keytool -import -alias mitre_ba_npe_ca3 -file MITRE\ BA\ NPE\ CA-3.crt -keystore "$JHOME/jre/lib/security/cacerts" -storepass changeit -noprompt
keytool -list -keystore "$JHOME/jre/lib/security/cacerts" -storepass changeit -noprompt >> cacerts_contents.txt
