FROM liquibase/liquibase:4.29
COPY liquibase/changelog/ /changelog/
WORKDIR /changelog
USER liquibase
ENTRYPOINT ["/bin/sh", "-c", "liquibase --changelog-file=db.changelog-master.xml --url=\"$LIQUIBASE_COMMAND_URL\" --username=\"$LIQUIBASE_COMMAND_USERNAME\" --password=\"$LIQUIBASE_COMMAND_PASSWORD\" update"]
