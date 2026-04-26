package com.bethibande.arcae.jpa.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class S3BackendMigration implements CustomTaskChange {

    private final ObjectMapper mapper = new JsonMapper();

    public record S3Config(
            String url,
            String region,
            String bucket,
            String accessKey,
            String secretKey
    ) {
    }

    @Override
    public void execute(final Database database) throws CustomChangeException {
        final Connection connection = database.getConnection().getUnderlyingConnection();
        try {
            final ResultSet result = connection.createStatement().executeQuery("SELECT repository.id as id, repository.settings AS settings FROM repository");
            final HashMap<S3Config, List<Long>> configs = new HashMap<>();
            while (result.next()) {
                final String settings = result.getString("settings");

                final JsonNode s3ConfigNode = mapper.readTree(settings).get("s3Config");
                final S3Config config = mapper.treeToValue(s3ConfigNode, S3Config.class);

                final long id = result.getLong("id");

                configs.computeIfAbsent(config, k -> new ArrayList<>()).add(id);
            }

            final PreparedStatement backendCreateStatement = connection.prepareStatement("""
                    INSERT INTO s3repositorybackend(id, name, uri, bucket, region, accesskey, secretkey)
                    VALUES (nextval('s3repositorybackend_seq'), ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """);

            final PreparedStatement repositoryUpdateStatement = connection.prepareStatement("""
                    UPDATE repository
                    SET backend_id = ?
                    WHERE id = ?
                    """);

            final Set<String> names = new HashSet<>();
            for (final Map.Entry<S3Config, List<Long>> entry : configs.entrySet()) {
                final S3Config config = entry.getKey();
                final List<Long> ids = entry.getValue();
                final String name = generateName(config, names);

                backendCreateStatement.setString(1, name);
                backendCreateStatement.setString(2, config.url());
                backendCreateStatement.setString(3, config.bucket());
                backendCreateStatement.setString(4, config.region());
                backendCreateStatement.setString(5, config.accessKey());
                backendCreateStatement.setString(6, config.secretKey());

                final long backendId;
                try (ResultSet rs = backendCreateStatement.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Creating S3 backend failed, no ID returned.");
                    }

                    backendId = rs.getLong("id");
                }

                for (final Long id : ids) {
                    repositoryUpdateStatement.setLong(1, backendId);
                    repositoryUpdateStatement.setLong(2, id);
                    repositoryUpdateStatement.executeUpdate();
                }
            }
        } catch (final SQLException | JsonProcessingException ex) {
            throw new CustomChangeException("Failed to migrate S3 backends", ex);
        }
    }

    protected String generateName(final S3Config config, final Set<String> names) {
        final String base = URI.create(config.url()).getHost();

        String name = base;
        int counter = 2;
        while (names.contains(name)) {
            name = base + "-" + counter++;
        }

        names.add(name);
        return name;
    }

    @Override
    public String getConfirmationMessage() {
        return "Migrated S3 backends";
    }

    @Override
    public void setUp() throws SetupException {

    }

    @Override
    public void setFileOpener(final ResourceAccessor resourceAccessor) {

    }

    @Override
    public ValidationErrors validate(final Database database) {
        return null;
    }
}
