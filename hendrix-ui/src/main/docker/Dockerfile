# Using centos 7 image
FROM tomcat:8-jre8
MAINTAINER ambud_sharma@symantec.com

RUN rm -rf /usr/local/tomcat/webapps/*

ADD ./ROOT.war /usr/local/tomcat/webapps/ROOT.war

RUN ls -lg /usr/local/tomcat/webapps/
 
CMD /usr/local/tomcat/bin/startup.sh && tail -f /usr/local/tomcat/logs/catalina.out