# Using centos 7 image
FROM java:8-jre
MAINTAINER ambud_sharma@symantec.com

RUN mkdir -p /usr/local/hendrix/
RUN mkdir -p /opt/hendrix/

ADD ./api.jar /usr/local/hendrix/api.jar
ADD ./config.yaml /opt/hendrix/
ADD ./template.properties /opt/hendrix/
ADD ./run.sh /opt/hendrix/

RUN apt-get -y update
RUN apt-get -y --force-yes install gettext mysql-client telnet wget
RUN chmod +x /opt/hendrix/*.sh

#RUN wget http://apache.cs.utah.edu/kafka/0.8.2.2/kafka_2.9.2-0.8.2.2.tgz

CMD /opt/hendrix/run.sh