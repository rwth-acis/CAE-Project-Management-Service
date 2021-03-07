FROM openjdk:14-jdk-alpine

ENV HTTP_PORT=8080
ENV HTTPS_PORT=8443
ENV LAS2PEER_PORT=9011

RUN apk add --update bash mysql-client curl git wget && rm -f /var/cache/apk/*
RUN addgroup -g 1000 -S las2peer && \
    adduser -u 1000 -S las2peer -G las2peer

COPY --chown=las2peer:las2peer . /src
WORKDIR /src
RUN git clone https://github.com/ettore26/wait-for-command

# run the rest as unprivileged user
USER las2peer
RUN chmod +x gradlew && ./gradlew build --exclude-task test

EXPOSE $HTTP_PORT
EXPOSE $HTTPS_PORT
EXPOSE $LAS2PEER_PORT
ENTRYPOINT ["/src/docker-entrypoint.sh"]