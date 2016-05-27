# Using centos 7 image
FROM java:8-jre
MAINTAINER ambud_sharma@symantec.com

RUN mkdir -p /usr/local/hendrix/
RUN mkdir -p /opt/hendrix/

ADD ./api.jar /usr/local/hendrix/api.jar
ADD ./config.yaml /opt/hendrix/

RUN ls -lg /usr/local/hendrix/
 
CMD java -jar /usr/local/hendrix/api.jar server /opt/hendrix/config.yaml