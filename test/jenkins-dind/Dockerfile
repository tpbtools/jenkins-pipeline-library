ARG TAG=2.204.3
FROM tpbtools/jenkins-dind:${TAG}
RUN apk add --no-cache rsync openjdk8 jq
USER jenkins
COPY --chown=jenkins:jenkins config /var/jenkins_home/
RUN install-plugins.sh < /var/jenkins_home/plugins.txt
USER root
RUN wget https://raw.githubusercontent.com/tpbtools/docker-command-launcher/master/kd.sh -O /usr/sbin/kd -q && \
    chmod +x /usr/sbin/kd && \
    curl https://raw.githubusercontent.com/tpbtools/devcontrol/master/install.sh | bash
