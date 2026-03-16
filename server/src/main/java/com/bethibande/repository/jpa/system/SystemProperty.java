package com.bethibande.repository.jpa.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Type;

@Entity
public class SystemProperty extends PanacheEntity {

    public static <T> T get(final String key, final TypeReference<T> type, final ObjectMapper mapper) {
        final SystemProperty systemProperty = SystemProperty.find("key", key).firstResult();
        return systemProperty == null ? null : systemProperty.valueAs(type, mapper);
    }

    public static <T> T get(final String key, final Class<T> type, final ObjectMapper mapper) {
        final SystemProperty systemProperty = SystemProperty.find("key", key).firstResult();
        return systemProperty == null ? null : systemProperty.valueAs(type, mapper);
    }

    public static void set(final String key, final Object value, final ObjectMapper mapper) {
        try {
            final String json = mapper.writeValueAsString(value);

            SystemProperty.getEntityManager().createNativeQuery("""
            INSERT INTO systemproperty (id, key, value)
            VALUES (nextval('systemproperty_seq'), :key, CAST(:value AS jsonb))
            ON CONFLICT (key)
            DO UPDATE SET value = EXCLUDED.value
        """).setParameter("key", key)
                    .setParameter("value", json)
                    .executeUpdate();
        } catch (final JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize system property value", ex);
        }
    }

    @Column(unique = true, nullable = false, columnDefinition = "varchar(255)")
    public String key;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    public String value;

    public <T> T valueAs(final TypeReference<T> type, final ObjectMapper mapper) {
        try {
            return mapper.readValue(this.value, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize system property value", e);
        }
    }

    public <T> T valueAs(final Class<T> type, final ObjectMapper mapper) {
        try {
            return mapper.readValue(this.value, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize system property value", e);
        }
    }
}
